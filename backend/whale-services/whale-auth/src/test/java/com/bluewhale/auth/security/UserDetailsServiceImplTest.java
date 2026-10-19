package com.bluewhale.auth.security;

import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.auth.service.SysRoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link UserDetailsServiceImpl} 单元测试（AUTH-23 / T-DDD-4）：经仓储加载 User 聚合、
 * 角色装配与启用状态判定。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    private static final long USER_ID = 9L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SysRoleService roleService;

    @InjectMocks
    private UserDetailsServiceImpl service;

    private static User user(Integer status) {
        return User.reconstitute(USER_ID, Username.of("tester"), EncryptedPassword.of("$2a$cipher"),
                "T", null, null, null, status, null, null);
    }

    @Test
    @DisplayName("用户存在且启用：返回 LoginUser 并装配角色")
    void shouldLoadEnabledUser() {
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user(1)));
        when(roleService.listRoleCodesByUserId(USER_ID)).thenReturn(List.of("super_admin"));

        UserDetails details = service.loadUserByUsername("tester");

        assertEquals("tester", details.getUsername());
        assertTrue(details.isEnabled());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> "super_admin".equals(a.getAuthority())));
    }

    @Test
    @DisplayName("用户不存在：抛 UsernameNotFoundException")
    void shouldThrowWhenUserMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("ghost"));
    }

    @Test
    @DisplayName("用户停用（status=0）：isEnabled=false")
    void shouldMarkDisabledWhenStatusZero() {
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user(0)));
        when(roleService.listRoleCodesByUserId(USER_ID)).thenReturn(List.of());

        assertFalse(service.loadUserByUsername("tester").isEnabled());
    }

    @Test
    @DisplayName("status 为 null：重建时归一化为停用，isEnabled=false")
    void shouldMarkDisabledWhenStatusNull() {
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user(null)));
        when(roleService.listRoleCodesByUserId(USER_ID)).thenReturn(List.of());

        assertFalse(service.loadUserByUsername("tester").isEnabled());
    }
}
