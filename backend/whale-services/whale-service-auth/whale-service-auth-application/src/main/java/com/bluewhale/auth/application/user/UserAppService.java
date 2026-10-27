package com.bluewhale.auth.application.user;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.tenant.TenantMembershipAppService;
import com.bluewhale.auth.application.user.command.AssignUserRolesCommand;
import com.bluewhale.auth.application.user.command.ChangeUserPasswordCommand;
import com.bluewhale.auth.application.user.command.ChangeUserStatusCommand;
import com.bluewhale.auth.application.user.command.CreateUserCommand;
import com.bluewhale.auth.application.user.command.UpdateUserCommand;
import com.bluewhale.auth.application.user.query.UserPageQuery;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.role.repository.RoleRepository;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.auth.vo.UserVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户管理应用服务：编排用户聚合与用户-角色关联的管理端用例（REQ-ARCH-001 / T-DDD-9）。
 *
 * <p>依赖方向遵循 DDD 四层约定：仅依赖领域层聚合/值对象/仓储端口、口令加密器与领域事件发布端口，
 * 不依赖 {@code entity}/{@code mapper}/{@code infrastructure}/{@code service}。返回视图对象或
 * {@link PageResult}，不向调用方泄漏领域对象与持久化对象。</p>
 *
 * <p>语义与旧 {@code SysUserServiceImpl} 逐条对齐：同样的校验顺序、异常文案与业务码；
 * 启停 / 删除 / 角色变更等写操作改发领域事件，由基础设施层监听器在事务提交后失效用户权限缓存；
 * 事务边界仅放在多写方法上。</p>
 *
 * @author BlueWhale
 */
@Service
public class UserAppService {

    /** 状态：启用 */
    private static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    private static final int STATUS_DISABLED = 0;

    /** User 聚合仓储 */
    private final UserRepository userRepository;

    /** Role 聚合仓储（仅用于校验待分配角色是否全部有效） */
    private final RoleRepository roleRepository;

    /** 口令加密器（明文口令落库前统一加密） */
    private final PasswordEncoder passwordEncoder;

    /** 领域事件发布端口（缓存失效等副作用由基础设施层监听） */
    private final DomainEventPublisher domainEventPublisher;

    /** 用户-租户关系服务（一人多租户：新建账号写首页关系） */
    private final TenantMembershipAppService tenantMembershipAppService;

    /**
     * 构造器注入
     *
     * @param userRepository 用户聚合仓储
     * @param roleRepository 角色聚合仓储
     * @param passwordEncoder 口令加密器
     * @param domainEventPublisher 领域事件发布端口
     * @param tenantMembershipAppService 用户-租户关系服务
     */
    public UserAppService(UserRepository userRepository, RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder, DomainEventPublisher domainEventPublisher,
                          TenantMembershipAppService tenantMembershipAppService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.domainEventPublisher = domainEventPublisher;
        this.tenantMembershipAppService = tenantMembershipAppService;
    }

    /**
     * 分页查询用户（关键字模糊匹配用户名/昵称/手机号，按创建时间降序）
     *
     * @param query 用户分页查询条件
     * @return 用户分页结果
     */
    public PageResult<UserVO> page(UserPageQuery query) {
        UserRepository.UserPage page = userRepository.page(query.current(), query.size(), query.keyword());
        List<UserVO> records = page.records().stream().map(UserVO::from).toList();
        return PageResult.of(records, page.total(), query.current(), query.size());
    }

    /**
     * 查询用户详情
     *
     * @param id 用户 ID
     * @return 用户视图对象
     */
    public UserVO getDetail(Long id) {
        return UserVO.from(requireUser(id));
    }

    /**
     * 创建用户：用户名值对象前置校验 → 用户名唯一性校验 → BCrypt 加密 → 保存
     *
     * @param command 创建用户命令
     * @return 新建用户视图对象
     */
    public UserVO create(CreateUserCommand command) {
        Long tenantId = TenantContext.requireId();
        // 值对象前置校验用户名不变量
        Username username = Username.of(command.username());
        if (userRepository.existsByUsername(tenantId, username.value())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "用户名已存在");
        }
        EncryptedPassword password = EncryptedPassword.of(passwordEncoder.encode(command.rawPassword()));
        User user = User.create(tenantId, username, password, command.nickname(), command.phone(),
                command.email(), command.avatar(), command.deptId(), command.status());
        // 保存并回填数据库生成的主键与时间戳
        User persisted = userRepository.save(user);
        // 一人多租户：新账号必须拥有首页租户关系，否则切换器与刷新校验都会判为无关系
        tenantMembershipAppService.ensureHomeMembership(persisted.getId(), tenantId);
        domainEventPublisher.publish(new UserDomainEvent(persisted.getId(), ChangeType.CREATED));
        return UserVO.from(persisted);
    }

    /**
     * 更新用户：口令非空时重置，其余资料字段全量覆盖，状态非空时变更
     *
     * @param id 用户 ID
     * @param command 更新用户命令
     * @return 更新后的用户视图对象
     */
    public UserVO update(Long id, UpdateUserCommand command) {
        User user = requireUser(id);
        int previousStatus = user.getStatus();
        boolean passwordChanged = command.rawPassword() != null && !command.rawPassword().isBlank();
        if (passwordChanged) {
            user.changePassword(EncryptedPassword.of(passwordEncoder.encode(command.rawPassword())));
        }
        user.changeProfile(command.nickname(), command.phone(), command.email(), command.avatar(),
                command.deptId());
        boolean statusChanged = command.status() != null && command.status() != previousStatus;
        if (command.status() != null) {
            user.changeStatus(command.status());
        }
        userRepository.save(user);
        domainEventPublisher.publish(new UserDomainEvent(id,
                resolveAccessChangeType(passwordChanged, statusChanged, user.getStatus())));
        return UserVO.from(user);
    }

    /**
     * 解析用户更新后需发布的事件类型。
     *
     * <p>停用优先（需同时失效权限缓存与撤销存量令牌）；其次改密（撤销全部存量令牌）；
     * 再次启停（失效缓存）；仅资料变更时沿用 {@code UPDATED}，不触发缓存/令牌动作。</p>
     *
     * @param passwordChanged 本次是否修改口令
     * @param statusChanged 本次状态是否变化
     * @param currentStatus 变更后的状态
     * @return 事件变更类型
     */
    private ChangeType resolveAccessChangeType(boolean passwordChanged, boolean statusChanged,
                                               int currentStatus) {
        if (statusChanged && currentStatus == STATUS_DISABLED) {
            return ChangeType.USER_DISABLED;
        }
        if (passwordChanged) {
            return ChangeType.PASSWORD_CHANGED;
        }
        if (statusChanged) {
            return ChangeType.STATUS_CHANGED;
        }
        return ChangeType.UPDATED;
    }

    /**
     * 重置用户口令：明文经 BCrypt 加密后以密文值对象承载
     *
     * @param id 用户 ID
     * @param command 重置口令命令
     */
    public void changePassword(Long id, ChangeUserPasswordCommand command) {
        User user = requireUser(id);
        user.changePassword(EncryptedPassword.of(passwordEncoder.encode(command.rawPassword())));
        userRepository.save(user);
        domainEventPublisher.publish(new UserDomainEvent(id, ChangeType.PASSWORD_CHANGED));
    }

    /**
     * 变更用户状态：校验目标状态合法后保存，并发布状态变更事件
     *
     * @param id 用户 ID
     * @param command 变更状态命令
     */
    public void changeStatus(Long id, ChangeUserStatusCommand command) {
        User user = requireUser(id);
        Integer status = command.status();
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        user.changeStatus(status);
        userRepository.save(user);
        // 停用发布专用事件：失效缓存并撤销全部存量令牌；启用仅失效缓存
        domainEventPublisher.publish(new UserDomainEvent(id,
                status == STATUS_DISABLED ? ChangeType.USER_DISABLED : ChangeType.STATUS_CHANGED));
    }

    /**
     * 删除用户：逻辑删除用户并清理用户-角色关联，随后发布删除事件
     *
     * @param id 用户 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireUser(id);
        userRepository.deleteById(id);
        // 用户删除后发布事件，由监听器失效其权限缓存
        domainEventPublisher.publish(new UserDomainEvent(id, ChangeType.DELETED));
    }

    /**
     * 全量覆盖用户角色：校验角色有效性后重建关联，并发布角色分配事件
     *
     * @param id 用户 ID
     * @param command 分配角色命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, AssignUserRolesCommand command) {
        requireUser(id);
        List<Long> roleIds = command.roleIds() == null ? List.of() : command.roleIds();
        if (!roleIds.isEmpty() && roleRepository.countByIds(roleIds) != roleIds.size()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在无效的角色ID");
        }
        userRepository.replaceRoles(id, roleIds);
        // 用户角色变更后发布事件，由监听器在提交后失效其权限缓存
        domainEventPublisher.publish(new UserDomainEvent(id, ChangeType.ROLES_ASSIGNED));
    }

    /**
     * 查询用户已分配的角色 ID 列表
     *
     * @param id 用户 ID
     * @return 角色 ID 列表
     */
    public List<Long> listRoleIds(Long id) {
        return userRepository.listRoleIds(id);
    }

    /**
     * 加载用户聚合，不存在时抛出资源不存在异常
     *
     * @param id 用户 ID
     * @return 用户聚合根
     */
    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
    }
}
