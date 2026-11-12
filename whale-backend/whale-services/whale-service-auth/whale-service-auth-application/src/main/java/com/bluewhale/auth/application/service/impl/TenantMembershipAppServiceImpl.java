package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.result.TenantResult;
import com.bluewhale.auth.application.model.vo.TenantMemberVO;
import com.bluewhale.auth.application.service.TenantAppService;
import com.bluewhale.auth.application.service.TenantMembershipAppService;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.aggregate.Role;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.auth.domain.aggregate.Tenant;
import com.bluewhale.auth.domain.model.TenantExpiryClock;
import com.bluewhale.auth.domain.repository.TenantRepository;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.auth.domain.repository.UserTenantRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户-租户成员关系应用服务（Wave 5.54 Phase 5.1：一人多租户）。
 *
 * <p><b>职责边界</b>：只回答「该用户可进入哪些租户」以及切换/刷新时的关系校验；
 * 角色与权限仍由 {@code sys_role.tenant_id} 与业务表行级 {@code tenant_id} 过滤保证，
 * 本服务不放宽任何业务数据隔离。</p>
 *
 * <p><b>失败语义</b>：主动切换时关系缺失或目标租户不可用一律 403；刷新链路复用
 * 已签发令牌时关系失效一律 401，需要重新登录。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantMembershipAppServiceImpl implements TenantMembershipAppService {

    private final UserTenantRepository userTenantRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantAppService tenantAppService;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 写入用户首页租户关系（幂等；新建账号与租户开通时调用）
     *
     * @param userId 用户 ID
     * @param tenantId 首页租户 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensureHomeMembership(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        userTenantRepository.ensureEnabled(userId, tenantId);
    }

    /**
     * 列出当前用户可进入的租户（仅启用关系且租户当前可用）
     *
     * @param userId 用户 ID
     * @param currentTenantId 当前令牌租户 ID
     * @return 租户列表（标记当前项）
     */
    public List<TenantResult> listMyTenants(Long userId, Long currentTenantId) {
        LocalDateTime now = TenantExpiryClock.now();
        return userTenantRepository.listEnabledTenantIds(userId).stream()
                .map(tenantRepository::findById)
                .flatMap(Optional::stream)
                .filter(tenant -> tenant.isAvailableAt(now))
                .map(tenant -> TenantResult.from(tenant, tenant.getId().equals(currentTenantId)))
                .toList();
    }

    /**
     * 校验刷新链路的关系仍然有效（撤销关系后 refresh 立即 401）
     *
     * @param userId 用户 ID
     * @param tenantId 令牌租户 ID
     * @throws BizException 关系缺失或停用时抛出 401
     */
    public void requireEnabledMembership(Long userId, Long tenantId) {
        if (!userTenantRepository.existsEnabled(userId, tenantId)) {
            throw new BizException(401, "租户访问关系已失效，请重新登录");
        }
    }

    /**
     * 校验主动切换目标：关系存在且目标租户当前可用
     *
     * @param userId 用户 ID
     * @param tenantId 目标租户 ID
     * @throws BizException 非成员或目标租户停用/过期时抛出 403
     */
    public void requireSwitchable(Long userId, Long tenantId) {
        if (!userTenantRepository.existsEnabled(userId, tenantId)) {
            throw new BizException(403, "无权进入该租户");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BizException(403, "租户不存在或已停用"));
        if (!tenant.isAvailableAt(TenantExpiryClock.now())) {
            throw new BizException(403, "目标租户已停用或已过期");
        }
    }

    /**
     * 列出当前租户的启用成员（Wave 5.54 Phase 5.3）。
     *
     * <p>用户基本信息按全局 {@code userId} 跨租户加载；角色只取当前租户内的关联。
     * 组装走 3 次批量查询（用户 → 角色关联 → 角色聚合），避免逐成员 3N 次查询。</p>
     *
     * @return 成员列表；无成员时返回空列表
     */
    public List<TenantMemberVO> listMembers() {
        Long tenantId = TenantContext.requireId();
        List<Long> userIds = userTenantRepository.listEnabledUserIds(tenantId);
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, UserAggregate> usersById = userRepository.findByIdsAcrossTenants(userIds).stream()
                .collect(Collectors.toMap(UserAggregate::getId, user -> user, (first, second) -> first));
        Map<Long, List<Long>> roleIdsByUserId = userRepository.listRoleIdsByUserIds(userIds);
        List<Long> allRoleIds = roleIdsByUserId.values().stream()
                .flatMap(List::stream).distinct().toList();
        Map<Long, Role> rolesById = allRoleIds.isEmpty() ? Map.of()
                : roleRepository.listEnabledByIds(allRoleIds).stream()
                        .filter(role -> tenantId.equals(role.getTenantId()))
                        .collect(Collectors.toMap(Role::getId, role -> role, (first, second) -> first));
        return userIds.stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .map(user -> toMember(user, roleIdsByUserId, rolesById))
                .toList();
    }

    /**
     * 全量覆盖成员在当前租户内的角色（只影响本租户角色关联）。
     *
     * @param userId 用户 ID
     * @param roleIds 目标角色 ID 列表（必须全部属于当前租户）
     * @throws BizException 非本租户成员或角色无效时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignMemberRoles(Long userId, List<Long> roleIds) {
        Long tenantId = TenantContext.requireId();
        if (!userTenantRepository.existsEnabled(userId, tenantId)) {
            throw new BizException(404, "成员不存在或关系已停用");
        }
        UserAggregate member = userRepository.findByIdAcrossTenants(userId)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        List<Long> targets = roleIds == null ? List.of() : roleIds;
        // 领域侧归一化（去重去空 + 数量上限），以归一化结果做有效性校验与落库
        List<Long> normalized = member.assignRoles(targets);
        if (!normalized.isEmpty() && roleRepository.countByIds(normalized) != normalized.size()) {
            throw new BizException(400, "存在无效的角色ID");
        }
        userRepository.replaceRoles(userId, normalized);
        domainEventPublisher.publish(new UserDomainEvent(userId, ChangeType.ROLES_ASSIGNED, tenantId));
    }

    /**
     * 平台租户为用户绑定可进入的租户关系（幂等）。
     *
     * @param userId 用户 ID
     * @param tenantId 目标租户 ID
     * @throws BizException 用户/租户不存在或租户不可用时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindTenant(Long userId, Long tenantId) {
        tenantAppService.requirePlatformTenant();
        userRepository.findByIdAcrossTenants(userId)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BizException(404, "租户不存在"));
        if (!tenant.isAvailableAt(TenantExpiryClock.now())) {
            throw new BizException(400, "目标租户已停用或已过期");
        }
        userTenantRepository.ensureEnabled(userId, tenantId);
        domainEventPublisher.publish(new UserDomainEvent(userId, ChangeType.ROLES_ASSIGNED, tenantId));
    }

    /**
     * 平台租户解除用户与租户的关系（软停用；首页关系禁止删除）。
     *
     * <p>解绑同时清理该用户在本租户的角色关联，并发布 {@code TENANT_UNBOUND} 事件：
     * 事务提交后失效权限缓存并写入成员撤销时间戳，使已签发的本租户 accessToken
     * 在网关与 auth 服务两侧立即失效；重新绑定后需重新分配角色并重新登录。</p>
     *
     * @param userId 用户 ID
     * @param tenantId 目标租户 ID
     * @throws BizException 首页关系、关系不存在或用户不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void unbindTenant(Long userId, Long tenantId) {
        tenantAppService.requirePlatformTenant();
        UserAggregate user = userRepository.findByIdAcrossTenants(userId)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        if (tenantId.equals(user.getTenantId())) {
            throw new BizException(400, "首页租户关系不允许解除");
        }
        if (!userTenantRepository.exists(userId, tenantId)) {
            throw new BizException(404, "租户关系不存在");
        }
        userTenantRepository.disable(userId, tenantId);
        userRepository.removeRolesInTenant(userId, tenantId);
        domainEventPublisher.publish(new UserDomainEvent(userId, ChangeType.TENANT_UNBOUND, tenantId));
    }

    /**
     * 组装成员视图（用户信息 + 当前租户内角色）
     *
     * @param user 用户聚合
     * @param roleIdsByUserId userId → 当前租户内角色 ID 列表
     * @param rolesById 角色 ID → 角色聚合（仅当前租户启用角色）
     * @return 成员视图
     */
    private TenantMemberVO toMember(UserAggregate user, Map<Long, List<Long>> roleIdsByUserId,
                                    Map<Long, Role> rolesById) {
        TenantMemberVO member = new TenantMemberVO();
        member.setUserId(user.getId());
        member.setUsername(user.getUsername().value());
        member.setNickname(user.getNickname());
        member.setPhone(user.getPhone());
        member.setStatus(user.getStatus());
        List<Long> roleIds = roleIdsByUserId.getOrDefault(user.getId(), List.of());
        member.setRoles(roleIds.stream()
                .map(rolesById::get)
                .filter(Objects::nonNull)
                .map(role -> TenantMemberVO.RoleItem.of(role.getId(),
                        role.getRoleCode().value(), role.getRoleName()))
                .toList());
        return member;
    }
}
