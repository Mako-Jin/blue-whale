package com.bluewhale.auth.application.tenant;

import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.domain.tenant.repository.TenantRepository;
import com.bluewhale.auth.domain.user.repository.UserTenantRepository;
import com.bluewhale.auth.vo.MyTenantVO;
import com.bluewhale.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantMembershipAppService {

    private final UserTenantRepository userTenantRepository;
    private final TenantRepository tenantRepository;

    /**
     * 写入用户首页租户关系（幂等；新建账号与租户开通时调用）
     *
     * @param userId 用户 ID
     * @param tenantId 首页租户 ID
     */
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
    public List<MyTenantVO> listMyTenants(Long userId, Long currentTenantId) {
        LocalDateTime now = LocalDateTime.now();
        return userTenantRepository.listEnabledTenantIds(userId).stream()
                .map(tenantRepository::findById)
                .flatMap(Optional::stream)
                .filter(tenant -> tenant.isAvailableAt(now))
                .map(tenant -> MyTenantVO.from(tenant, tenant.getId().equals(currentTenantId)))
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
        if (!tenant.isAvailableAt(LocalDateTime.now())) {
            throw new BizException(403, "目标租户已停用或已过期");
        }
    }
}
