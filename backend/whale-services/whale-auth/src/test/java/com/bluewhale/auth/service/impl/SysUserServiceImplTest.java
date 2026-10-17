package com.bluewhale.auth.service.impl;

import com.bluewhale.auth.dto.ChangePasswordRequest;
import com.bluewhale.auth.dto.UserStatusRequest;
import com.bluewhale.auth.entity.SysUser;
import com.bluewhale.auth.mapper.SysUserMapper;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysUserServiceImpl} 单元测试：改密与启停接口（AUTH-22）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    private static final long USER_ID = 2L;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private SysUserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new SysUserServiceImpl(null, null, passwordEncoder);
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    private static SysUser user(int status) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("tester");
        user.setStatus(status);
        return user;
    }

    private static ChangePasswordRequest passwordRequest(String password) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setPassword(password);
        return request;
    }

    private static UserStatusRequest statusRequest(Integer status) {
        UserStatusRequest request = new UserStatusRequest();
        request.setStatus(status);
        return request;
    }

    @Test
    @DisplayName("changePassword：明文经 BCrypt 加密后更新，落库非明文")
    void shouldEncodePasswordBeforeUpdate() {
        SysUser stored = user(1);
        when(userMapper.selectById(USER_ID)).thenReturn(stored);
        when(passwordEncoder.encode("newpwd123")).thenReturn("$2a$cipher");

        userService.changePassword(USER_ID, passwordRequest("newpwd123"));

        assertEquals("$2a$cipher", stored.getPassword());
        verify(userMapper).updateById(stored);
    }

    @Test
    @DisplayName("changePassword：用户不存在抛 BizException 且不落库")
    void shouldRejectPasswordChangeWhenUserMissing() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThrows(BizException.class,
                () -> userService.changePassword(USER_ID, passwordRequest("newpwd123")));
        verify(userMapper, never()).updateById(org.mockito.ArgumentMatchers.any(SysUser.class));
    }

    @Test
    @DisplayName("changeStatus：合法状态（0 停用）落库")
    void shouldUpdateStatusWhenValid() {
        SysUser stored = user(1);
        when(userMapper.selectById(USER_ID)).thenReturn(stored);

        userService.changeStatus(USER_ID, statusRequest(0));

        assertEquals(0, stored.getStatus());
        verify(userMapper).updateById(stored);
    }

    @Test
    @DisplayName("changeStatus：非法状态抛 BizException 且不落库")
    void shouldRejectIllegalStatus() {
        SysUser stored = user(1);
        when(userMapper.selectById(USER_ID)).thenReturn(stored);

        assertThrows(BizException.class, () -> userService.changeStatus(USER_ID, statusRequest(9)));
        verify(userMapper, never()).updateById(org.mockito.ArgumentMatchers.any(SysUser.class));
    }
}
