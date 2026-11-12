package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.AuthenticatedUser;
import com.bluewhale.auth.application.model.command.ChangeOwnPasswordCommand;
import com.bluewhale.auth.application.port.CurrentUserProvider;
import com.bluewhale.auth.application.port.PasswordHasher;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 自助改密应用服务（REQ-SEC-023）：当前登录用户修改本人口令。
 *
 * <p>校验顺序：旧口令正确性 → 新口令强度 → 历史复用 → 改密并清除强制改密标记 → 记录历史。
 * 改密成功后发布 {@code PASSWORD_CHANGED} 领域事件，由基础设施层写入用户级撤销时间戳，
 * 使该用户全部存量 access/refresh 令牌立即失效（需重新登录）。</p>
 *
 * @author BlueWhale
 */
public interface PasswordAppService {

    /**
     * 修改当前登录用户口令
     *
     * @param command 自助改密命令（旧口令 + 新口令）
     */
    public void changeOwnPassword(ChangeOwnPasswordCommand command);
}
