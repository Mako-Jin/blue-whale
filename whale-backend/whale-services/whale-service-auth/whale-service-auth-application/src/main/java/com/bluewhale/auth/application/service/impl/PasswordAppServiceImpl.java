package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.AuthenticatedUser;
import com.bluewhale.auth.application.model.command.ChangeOwnPasswordCommand;
import com.bluewhale.auth.application.port.CurrentUserProvider;
import com.bluewhale.auth.application.port.PasswordHasher;
import com.bluewhale.auth.application.service.PasswordAppService;
import com.bluewhale.auth.application.service.PasswordPolicyService;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 自助改密应用服务（REQ-SEC-023）：当前登录用户修改本人口令。
 *
 * <p>校验顺序：旧口令正确性 → 新口令强度 → 历史复用 → 改密并清除强制改密标记 → 记录历史。
 * 改密成功后发布 {@code PASSWORD_CHANGED} 领域事件，由基础设施层写入用户级撤销时间戳，
 * 使该用户全部存量 access/refresh 令牌立即失效（需重新登录）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PasswordAppServiceImpl implements PasswordAppService {

    /** 旧口令错误统一文案（不区分账号不存在与口令错误，避免探测） */
    private static final String OLD_PASSWORD_MISMATCH = "原口令不正确";

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicyService passwordPolicyService;
    private final DomainEventPublisher domainEventPublisher;
    /** 编程式事务模板（事务边界精确到写路径，避免慢哈希比对占用数据库连接） */
    private final TransactionTemplate transactionTemplate;

    /**
     * 修改当前登录用户口令
     *
     * <p>旧口令比对是 BCrypt 慢哈希，放在事务外完成，避免长时间占用数据库连接；
     * 比对通过后的写库、历史记录与事件发布由 {@link TransactionTemplate} 保证原子回滚
     * （语义等价于 {@code @Transactional}）。</p>
     *
     * @param command 自助改密命令（旧口令 + 新口令）
     */
    public void changeOwnPassword(ChangeOwnPasswordCommand command) {
        AuthenticatedUser current = currentUserProvider.current();
        // sys_user 归属首页租户，令牌租户可能是切换后的其他租户，因此按全局 userId 跨租户加载
        UserAggregate user = userRepository.findByIdAcrossTenants(current.userId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        // 旧口令比对保持在事务外（慢哈希），事务只包住后续读校验与写路径
        if (!passwordHasher.matches(command.oldPassword(), user.getPassword().value())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "auth.password.old-mismatch",
                    OLD_PASSWORD_MISMATCH);
        }
        transactionTemplate.executeWithoutResult(status -> doChangeOwnPassword(user, command));
    }

    /**
     * 事务内写路径：新口令强度与复用校验 → 改密并清除强制改密标记 → 落库 → 记录历史 → 发布事件
     *
     * @param user 已通过旧口令比对的用户聚合
     * @param command 自助改密命令
     */
    private void doChangeOwnPassword(UserAggregate user, ChangeOwnPasswordCommand command) {
        passwordPolicyService.validate(command.newPassword(), user.getUsername().value());
        passwordPolicyService.ensureNotReused(user.getTenantId(), user.getId(),
                user.getPassword().value(), command.newPassword());
        user.changePassword(EncryptedPassword.of(passwordPolicyService.encode(command.newPassword())));
        user.clearMustChangePassword();
        userRepository.save(user);
        passwordPolicyService.recordHistory(user.getTenantId(), user.getId(), user.getPassword().value());
        domainEventPublisher.publish(new UserDomainEvent(user.getId(), ChangeType.PASSWORD_CHANGED));
    }
}
