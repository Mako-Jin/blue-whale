package com.bluewhale.auth.security;

import com.bluewhale.auth.entity.SysUser;
import com.bluewhale.auth.mapper.SysUserMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link UserDetailsServiceImpl} 单元测试（AUTH-23）：用户加载、角色装配与启用状态判定。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    private static final long USER_ID = 9L;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleService roleService;

    @InjectMocks
    private UserDetailsServiceImpl service;

    private static SysUser user(Integer status) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("tester");
        user.setPassword("$2a$cipher");
        user.setNickname("T");
        user.setStatus(status);
        return user;
    }

    @Test
    @DisplayName("用户存在且启用：返回 LoginUser 并装配角色")
    void shouldLoadEnabledUser() {
        when(userMapper.selectOne(any())).thenReturn(user(1));
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
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("ghost"));
    }

    @Test
    @DisplayName("用户停用（status=0）：isEnabled=false")
    void shouldMarkDisabledWhenStatusZero() {
        when(userMapper.selectOne(any())).thenReturn(user(0));
        when(roleService.listRoleCodesByUserId(USER_ID)).thenReturn(List.of());

        assertFalse(service.loadUserByUsername("tester").isEnabled());
    }

    @Test
    @DisplayName("status 为 null：按停用处理")
    void shouldMarkDisabledWhenStatusNull() {
        when(userMapper.selectOne(any())).thenReturn(user(null));
        when(roleService.listRoleCodesByUserId(USER_ID)).thenReturn(List.of());

        assertFalse(service.loadUserByUsername("tester").isEnabled());
    }
}
