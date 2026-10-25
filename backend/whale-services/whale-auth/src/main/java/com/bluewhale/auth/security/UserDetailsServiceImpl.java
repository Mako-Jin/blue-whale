package com.bluewhale.auth.security;

import com.bluewhale.auth.application.role.RoleAppService;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * UserDetailsService 实现：按用户名经 {@link UserRepository} 加载 User 聚合，再装配角色
 * （DDD 迁移 Step 2 / T-DDD-4，REQ-ARCH-001）。
 *
 * <p>对外行为保持不变：用户不存在抛 {@link UsernameNotFoundException}；启用状态按
 * {@code status == 1} 判定。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleAppService roleAppService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Long tenantId = LoginTenantContext.current();
        if (tenantId == null) {
            throw new UsernameNotFoundException("缺少租户上下文: " + username);
        }
        User user = userRepository.findByTenantIdAndUsername(tenantId, username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        // 角色必须按登录租户解析（Phase 5.1 起角色仓储要求租户上下文）
        Long previous = TenantContext.get();
        TenantContext.set(tenantId);
        try {
            List<String> roles = roleAppService.listRoleCodesByUserId(user.getId());
            return new LoginUser(tenantId, user.getId(), user.getUsername().value(), user.getPassword().value(),
                    user.getNickname(), roles, user.isEnabled());
        } finally {
            TenantContext.set(previous);
        }
    }

    /**
     * 按全局用户 ID 加载并装配「指定租户」下的角色（Wave 5.54 Phase 5.1）。
     *
     * <p>供刷新、切换与 JWT 过滤器使用：{@code sys_user} 归属首页租户，令牌租户可能是
     * 该用户切换后的其他租户，因此账号按 {@code userId} 跨租户加载，角色必须在目标
     * 租户上下文中解析（只取 {@code sys_role.tenant_id=目标租户} 的启用角色）。</p>
     *
     * @param userId 全局用户 ID（令牌 claim，切换前后不变）
     * @param tenantId 令牌租户 ID
     * @return 目标租户上下文下的登录用户
     * @throws UsernameNotFoundException 用户不存在或参数缺失
     */
    public LoginUser loadUserById(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            throw new UsernameNotFoundException("缺少用户或租户标识");
        }
        User user = userRepository.findByIdAcrossTenants(userId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + userId));
        Long previous = TenantContext.get();
        TenantContext.set(tenantId);
        try {
            List<String> roles = roleAppService.listRoleCodesByUserId(userId);
            return new LoginUser(tenantId, user.getId(), user.getUsername().value(), user.getPassword().value(),
                    user.getNickname(), roles, user.isEnabled());
        } finally {
            TenantContext.set(previous);
        }
    }
}
