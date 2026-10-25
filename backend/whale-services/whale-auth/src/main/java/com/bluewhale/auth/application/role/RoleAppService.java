package com.bluewhale.auth.application.role;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.role.command.AssignRolePermissionsCommand;
import com.bluewhale.auth.application.role.command.CreateRoleCommand;
import com.bluewhale.auth.application.role.command.UpdateRoleCommand;
import com.bluewhale.auth.application.role.query.RolePageQuery;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.RoleDomainEvent;
import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.permission.repository.PermissionRepository;
import com.bluewhale.auth.domain.role.aggregate.Role;
import com.bluewhale.auth.domain.role.repository.RoleRepository;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.auth.vo.RoleVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.lock.annotation.DistributedLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理应用服务：编排角色聚合与角色-权限关联的管理端用例（REQ-ARCH-001 / T-DDD-9）。
 *
 * <p>依赖方向遵循 DDD 四层约定：仅依赖领域层聚合/值对象/仓储端口与领域事件发布端口，不依赖
 * {@code entity}/{@code mapper}/{@code infrastructure}/{@code service}。返回视图对象或
 * {@link PageResult}，不向调用方泄漏领域对象与持久化对象。</p>
 *
 * <p>语义与旧 {@code SysRoleServiceImpl} 逐条对齐：同样的校验顺序、异常文案与业务码；
 * 角色删除与权限变更后发布领域事件，由基础设施层监听器在事务提交后失效该角色下用户的权限缓存；
 * {@code assignPermissions} 保留与旧实现一致的分布式锁（锁 {@code lock:auth:role:{id}}），
 * 事务边界仅放在多写方法上。</p>
 *
 * @author BlueWhale
 */
@Service
public class RoleAppService {

    /** Role 聚合仓储 */
    private final RoleRepository roleRepository;

    /** Permission 聚合仓储（仅用于校验待分配权限点是否全部有效） */
    private final PermissionRepository permissionRepository;

    /** 领域事件发布端口（缓存失效等副作用由基础设施层监听） */
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 构造器注入
     *
     * @param roleRepository 角色聚合仓储
     * @param permissionRepository 权限点聚合仓储
     * @param domainEventPublisher 领域事件发布端口
     */
    public RoleAppService(RoleRepository roleRepository, PermissionRepository permissionRepository,
                          DomainEventPublisher domainEventPublisher) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 分页查询角色（关键字模糊匹配角色编码/名称，按排序号升序、创建时间降序）
     *
     * @param query 角色分页查询条件
     * @return 角色分页结果
     */
    public PageResult<RoleVO> page(RolePageQuery query) {
        RoleRepository.RolePage page = roleRepository.page(query.current(), query.size(), query.keyword());
        List<RoleVO> records = page.records().stream().map(RoleVO::from).toList();
        return PageResult.of(records, page.total(), query.current(), query.size());
    }

    /**
     * 查询角色详情
     *
     * @param id 角色 ID
     * @return 角色视图对象
     */
    public RoleVO getDetail(Long id) {
        return RoleVO.from(requireRole(id));
    }

    /**
     * 创建角色：角色编码值对象前置校验 → 编码唯一性校验 → 保存
     *
     * @param command 创建角色命令
     * @return 新建角色视图对象
     */
    public RoleVO create(CreateRoleCommand command) {
        Long tenantId = TenantContext.requireId();
        // 值对象前置校验角色编码不变量
        RoleCode roleCode = RoleCode.of(command.roleCode());
        if (roleRepository.existsByCode(roleCode.value())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "角色编码已存在");
        }
        Role role = Role.create(tenantId, roleCode, command.roleName(), command.description(), command.sortNo(),
                command.status());
        // 保存并回填数据库生成的主键与时间戳
        Role persisted = roleRepository.save(role);
        domainEventPublisher.publish(new RoleDomainEvent(persisted.getId(), ChangeType.CREATED));
        return RoleVO.from(persisted);
    }

    /**
     * 更新角色：角色编码不可修改，名称/描述全量覆盖，排序号与状态非空时变更
     *
     * @param id 角色 ID
     * @param command 更新角色命令
     * @return 更新后的角色视图对象
     */
    public RoleVO update(Long id, UpdateRoleCommand command) {
        Role role = requireRole(id);
        role.rename(command.roleName());
        role.changeDescription(command.description());
        if (command.sortNo() != null) {
            role.changeSortNo(command.sortNo());
        }
        if (command.status() != null) {
            role.changeStatus(command.status());
        }
        roleRepository.save(role);
        domainEventPublisher.publish(new RoleDomainEvent(id, ChangeType.UPDATED));
        return RoleVO.from(role);
    }

    /**
     * 删除角色：逻辑删除角色并清理角色-权限关联，随后发布删除事件
     *
     * @param id 角色 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireRole(id);
        roleRepository.deleteById(id);
        // 角色删除后该角色下用户的权限码集合变化，发布事件由监听器失效其缓存
        domainEventPublisher.publish(new RoleDomainEvent(id, ChangeType.DELETED));
    }

    /**
     * 全量覆盖角色权限：校验权限点有效性后重建关联，并发布权限分配事件
     *
     * @param id 角色 ID
     * @param command 分配权限命令
     */
    @Transactional(rollbackFor = Exception.class)
    @DistributedLock(prefix = "lock", biz = "auth:role", key = "#id", waitTime = 1, leaseTime = 30, failFast = true)
    public void assignPermissions(Long id, AssignRolePermissionsCommand command) {
        requireRole(id);
        List<Long> permissionIds = command.permissionIds() == null ? List.of() : command.permissionIds();
        if (!permissionIds.isEmpty() && permissionRepository.countByIds(permissionIds) != permissionIds.size()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在无效的权限ID");
        }
        roleRepository.replacePermissions(id, permissionIds);
        // 角色权限变更后发布事件，由监听器在提交后失效该角色下用户的缓存
        domainEventPublisher.publish(new RoleDomainEvent(id, ChangeType.PERMISSIONS_ASSIGNED));
    }

    /**
     * 查询角色已分配的权限点 ID 列表
     *
     * @param id 角色 ID
     * @return 权限点 ID 列表
     */
    public List<Long> listPermissionIds(Long id) {
        return roleRepository.listPermissionIds(id);
    }

    /**
     * 查询用户拥有的启用角色编码（用户 → 角色，过滤停用角色）
     *
     * <p>与旧 {@code SysRoleServiceImpl#listRoleCodesByUserId} 逐行等价：先查用户角色 ID，
     * 为空直接返回空列表，再按启用状态过滤并映射为角色编码。</p>
     *
     * @param userId 用户 ID
     * @return 启用角色编码列表；用户无角色时返回空列表
     */
    public List<String> listRoleCodesByUserId(Long userId) {
        List<Long> roleIds = roleRepository.listRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.listEnabledByIds(roleIds).stream()
                .map(role -> role.getRoleCode().value())
                .toList();
    }

    /**
     * 查询用户经由角色获得的启用权限码（用户 → 角色 → 权限点，逐层去重并过滤停用）
     *
     * <p>与旧 {@code SysRoleServiceImpl#listPermissionCodesByUserId} 逐行等价：先查用户角色 ID，
     * 为空返回空列表；再查角色关联权限点 ID（去重），为空返回空列表；最后仅保留启用权限点的
     * 权限码（去重）。</p>
     *
     * @param userId 用户 ID
     * @return 权限码列表；用户无角色或无有效权限关联时返回空列表
     */
    public List<String> listPermissionCodesByUserId(Long userId) {
        List<Long> roleIds = roleRepository.listRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> permissionIds = roleRepository.listPermissionIdsByRoleIds(roleIds);
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.listEnabledPermissionCodesByIds(permissionIds);
    }

    /**
     * 加载角色聚合，不存在时抛出资源不存在异常
     *
     * @param id 角色 ID
     * @return 角色聚合根
     */
    private Role requireRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
    }
}
