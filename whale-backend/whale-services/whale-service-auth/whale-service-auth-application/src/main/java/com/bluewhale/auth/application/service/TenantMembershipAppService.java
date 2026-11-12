package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.result.TenantResult;
import com.bluewhale.auth.application.model.vo.TenantMemberVO;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.role.aggregate.Role;
import com.bluewhale.auth.domain.role.repository.RoleRepository;
import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.domain.tenant.aggregate.TenantExpiryClock;
import com.bluewhale.auth.domain.tenant.repository.TenantRepository;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.auth.domain.user.repository.UserTenantRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
public interface TenantMembershipAppService {

    /**
     * 写入用户首页租户关系（幂等；新建账号与租户开通时调用）
     *
     * @param userId 用户 ID
     * @param tenantId 首页租户 ID
     */
    void ensureHomeMembership(Long userId, Long tenantId);

    /**
     * 列出当前用户可进入的租户（仅启用关系且租户当前可用）
     *
     * @param userId 用户 ID
     * @param currentTenantId 当前令牌租户 ID
     * @return 租户列表（标记当前项）
     */
    List<TenantResult> listMyTenants(Long userId, Long currentTenantId);

    /**
     * 校验刷新链路的关系仍然有效（撤销关系后 refresh 立即 401）
     *
     * @param userId 用户 ID
     * @param tenantId 令牌租户 ID
     * @throws BizException 关系缺失或停用时抛出 401
     */
    void requireEnabledMembership(Long userId, Long tenantId);

    /**
     * 校验主动切换目标：关系存在且目标租户当前可用
     *
     * @param userId 用户 ID
     * @param tenantId 目标租户 ID
     * @throws BizException 非成员或目标租户停用/过期时抛出 403
     */
    void requireSwitchable(Long userId, Long tenantId);

    /**
     * 列出当前租户的启用成员（Wave 5.54 Phase 5.3）。
     *
     * <p>用户基本信息按全局 {@code userId} 跨租户加载；角色只取当前租户内的关联。</p>
     *
     * @return 成员列表；无成员时返回空列表
     */
    List<TenantMemberVO> listMembers();

    /**
     * 全量覆盖成员在当前租户内的角色（只影响本租户角色关联）。
     *
     * @param userId 用户 ID
     * @param roleIds 目标角色 ID 列表（必须全部属于当前租户）
     * @throws BizException 非本租户成员或角色无效时抛出
     */
    void assignMemberRoles(Long userId, List<Long> roleIds);

    /**
     * 平台租户为用户绑定可进入的租户关系（幂等）。
     *
     * @param userId 用户 ID
     * @param tenantId 目标租户 ID
     * @throws BizException 用户/租户不存在或租户不可用时抛出
     */
    void bindTenant(Long userId, Long tenantId);

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
    void unbindTenant(Long userId, Long tenantId);
}
