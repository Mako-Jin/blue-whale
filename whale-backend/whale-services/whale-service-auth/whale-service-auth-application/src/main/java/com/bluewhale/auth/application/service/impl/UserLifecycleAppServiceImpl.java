package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.CreateUserCommand;
import com.bluewhale.auth.application.model.command.UserLifecycleCommand;
import com.bluewhale.auth.application.model.vo.UserLifecycleVO;
import com.bluewhale.auth.application.model.vo.UserVO;
import com.bluewhale.auth.application.service.PasswordPolicyService;
import com.bluewhale.auth.application.service.UserAppService;
import com.bluewhale.auth.application.service.UserLifecycleAppService;
import com.bluewhale.auth.application.service.impl.TemporaryPasswordGenerator;
import com.bluewhale.auth.domain.event.AuthDomainEvent;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人员生命周期应用服务：供 OA 人事异动审批通过后同步 Auth 账号状态与部门。
 *
 * <p>动作语义：入职建号或启用、转正保活、调岗改部门、离职停用（不删除）、返聘启用。
 * 停用走 {@link User#disable()}，发布 {@code USER_DISABLED} 事件以撤销存量令牌并失效权限缓存；
 * 入职建号复用 {@link UserAppService#create(CreateUserCommand)}，一次性临时口令仅在本次响应返回。</p>
 *
 * @author BlueWhale
 */
@Service
public class UserLifecycleAppServiceImpl implements UserLifecycleAppService {

    /** 动作：入职 */
    private static final String ACTION_ONBOARD = "ONBOARD";
    /** 动作：转正 */
    private static final String ACTION_REGULAR = "REGULAR";
    /** 动作：调岗 */
    private static final String ACTION_TRANSFER = "TRANSFER";
    /** 动作：离职 */
    private static final String ACTION_LEAVE = "LEAVE";
    /** 动作：返聘 */
    private static final String ACTION_REHIRE = "REHIRE";

    /** 状态：启用 */
    private static final int STATUS_ENABLED = 1;

    private final UserRepository userRepository;
    private final UserAppService userAppService;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final PasswordPolicyService passwordPolicyService;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 构造器注入。
     *
     * @param userRepository 用户聚合仓储
     * @param userAppService 用户管理应用服务（复用建号口令策略）
     * @param temporaryPasswordGenerator 临时口令生成器
     * @param passwordPolicyService 口令策略服务
     * @param domainEventPublisher 领域事件发布端口
     */
    public UserLifecycleAppServiceImpl(UserRepository userRepository, UserAppService userAppService,
                                   TemporaryPasswordGenerator temporaryPasswordGenerator,
                                   PasswordPolicyService passwordPolicyService,
                                   DomainEventPublisher domainEventPublisher) {
        this.userRepository = userRepository;
        this.userAppService = userAppService;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.passwordPolicyService = passwordPolicyService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 执行人员生命周期动作。
     *
     * @param command 生命周期命令
     * @return 执行结果（入职建号时含一次性临时口令）
     */
    @Transactional(rollbackFor = Exception.class)
    public UserLifecycleVO apply(UserLifecycleCommand command) {
        String action = command.action() == null ? "" : command.action().trim().toUpperCase();
        return switch (action) {
            case ACTION_ONBOARD -> onboard(command);
            case ACTION_REGULAR -> regular(command);
            case ACTION_TRANSFER -> transfer(command);
            case ACTION_LEAVE -> leave(command);
            case ACTION_REHIRE -> rehire(command);
            default -> throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "不支持的生命周期动作：" + command.action());
        };
    }

    /**
     * 入职：未提供用户ID 时创建账号并生成一次性临时口令；已提供时启用并同步部门。
     *
     * @param command 命令
     * @return 执行结果
     */
    private UserLifecycleVO onboard(UserLifecycleCommand command) {
        if (command.userId() == null) {
            if (command.username() == null || command.username().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "入职建号必须提供登录账号");
            }
            String temporaryPassword = temporaryPasswordGenerator.generate(passwordPolicyService.minLength());
            CreateUserCommand createCommand = new CreateUserCommand(command.username(), temporaryPassword,
                    command.nickname(), command.phone(), command.email(), null, command.deptId(), STATUS_ENABLED);
            UserVO created = userAppService.create(createCommand);
            return new UserLifecycleVO(created.getId(), created.getUsername(), created.getStatus(),
                    created.getDeptId(), temporaryPassword);
        }
        User user = requireUser(command.userId());
        boolean changed = false;
        if (command.deptId() != null && !command.deptId().equals(user.getDeptId())) {
            user.changeProfile(user.getNickname(), user.getPhone(), user.getEmail(), user.getAvatar(), command.deptId());
            changed = true;
        }
        if (user.getStatus() != STATUS_ENABLED) {
            user.enable();
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
            domainEventPublisher.publish(new UserDomainEvent(user.getId(),
                    AuthDomainEvent.ChangeType.STATUS_CHANGED));
        }
        return toVO(user, null);
    }

    /**
     * 转正：Auth 不维护试用/正式状态，仅确保账号处于启用态。
     *
     * @param command 命令
     * @return 执行结果
     */
    private UserLifecycleVO regular(UserLifecycleCommand command) {
        User user = requireUser(command.userId());
        if (user.getStatus() != STATUS_ENABLED) {
            user.enable();
            userRepository.save(user);
            domainEventPublisher.publish(new UserDomainEvent(user.getId(),
                    AuthDomainEvent.ChangeType.STATUS_CHANGED));
        }
        return toVO(user, null);
    }

    /**
     * 调岗：仅变更部门，保留昵称/联系方式/头像。
     *
     * @param command 命令
     * @return 执行结果
     */
    private UserLifecycleVO transfer(UserLifecycleCommand command) {
        if (command.deptId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "调岗必须提供目标部门");
        }
        User user = requireUser(command.userId());
        user.changeProfile(user.getNickname(), user.getPhone(), user.getEmail(), user.getAvatar(), command.deptId());
        userRepository.save(user);
        domainEventPublisher.publish(new UserDomainEvent(user.getId(), AuthDomainEvent.ChangeType.UPDATED));
        return toVO(user, null);
    }

    /**
     * 离职：停用账号但不删除，并撤销存量令牌。
     *
     * @param command 命令
     * @return 执行结果
     */
    private UserLifecycleVO leave(UserLifecycleCommand command) {
        User user = requireUser(command.userId());
        user.disable();
        userRepository.save(user);
        domainEventPublisher.publish(new UserDomainEvent(user.getId(),
                AuthDomainEvent.ChangeType.USER_DISABLED));
        return toVO(user, null);
    }

    /**
     * 返聘：启用账号，可选同步新部门。
     *
     * @param command 命令
     * @return 执行结果
     */
    private UserLifecycleVO rehire(UserLifecycleCommand command) {
        User user = requireUser(command.userId());
        user.enable();
        if (command.deptId() != null) {
            user.changeProfile(user.getNickname(), user.getPhone(), user.getEmail(), user.getAvatar(), command.deptId());
        }
        userRepository.save(user);
        domainEventPublisher.publish(new UserDomainEvent(user.getId(),
                AuthDomainEvent.ChangeType.STATUS_CHANGED));
        return toVO(user, null);
    }

    /**
     * 按 ID 查询用户，缺失时抛业务异常。
     *
     * @param userId 用户 ID
     * @return 用户聚合
     */
    private User requireUser(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "生命周期动作必须提供用户ID");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR.getCode(), "用户不存在：" + userId));
    }

    /**
     * 用户聚合转生命周期结果。
     *
     * @param user 用户聚合
     * @param temporaryPassword 一次性临时口令
     * @return 结果
     */
    private UserLifecycleVO toVO(User user, String temporaryPassword) {
        return new UserLifecycleVO(user.getId(), user.getUsername().value(), user.getStatus(),
                user.getDeptId(), temporaryPassword);
    }
}
