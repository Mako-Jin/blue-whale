package com.bluewhale.auth.security;

import com.bluewhale.auth.application.role.RoleAppService;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
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
        List<String> roles = roleAppService.listRoleCodesByUserId(user.getId());
        return new LoginUser(tenantId, user.getId(), user.getUsername().value(), user.getPassword().value(),
                user.getNickname(), roles, user.isEnabled());
    }
}
