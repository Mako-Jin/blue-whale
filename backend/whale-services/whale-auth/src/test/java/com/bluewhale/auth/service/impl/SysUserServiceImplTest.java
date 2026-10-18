package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.auth.dto.ChangePasswordRequest;
import com.bluewhale.auth.dto.UserStatusRequest;
import com.bluewhale.auth.entity.SysRole;
import com.bluewhale.auth.entity.SysUser;
import com.bluewhale.auth.entity.SysUserRole;
import com.bluewhale.auth.mapper.SysRoleMapper;
import com.bluewhale.auth.mapper.SysUserMapper;
import com.bluewhale.auth.mapper.SysUserRoleMapper;
import com.bluewhale.auth.security.PermissionCache;
import com.bluewhale.common.core.exception.BizException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysUserServiceImpl} 单元测试：改密、启停、删除与角色分配接口（AUTH-22）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    private static final long USER_ID = 2L;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PermissionCache permissionCache;

    private SysUserServiceImpl userService;

    /**
     * 注册实体元信息缓存：`LambdaQueryWrapper#in` 会按实体解析列名，纯单测无 MyBatis 环境需手动初始化 TableInfo。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysRole.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
    }

    @BeforeEach
    void setUp() {
        userService = new SysUserServiceImpl(userRoleMapper, roleMapper, passwordEncoder, permissionCache);
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
        verify(permissionCache).evictUser(USER_ID);
    }

    @Test
    @DisplayName("changeStatus：非法状态抛 BizException 且不落库、不失效")
    void shouldRejectIllegalStatus() {
        SysUser stored = user(1);
        when(userMapper.selectById(USER_ID)).thenReturn(stored);

        assertThrows(BizException.class, () -> userService.changeStatus(USER_ID, statusRequest(9)));
        verify(userMapper, never()).updateById(org.mockito.ArgumentMatchers.any(SysUser.class));
        verify(permissionCache, never()).evictUser(any());
    }

    @Test
    @DisplayName("assignRoles：角色ID校验通过后重建用户-角色关联并失效权限缓存")
    void shouldAssignRolesAndEvictPermissionCache() {
        when(userMapper.selectById(USER_ID)).thenReturn(user(1));
        List<Long> roleIds = List.of(1L, 2L);
        when(roleMapper.selectCount(any())).thenReturn((long) roleIds.size());

        userService.assignRoles(USER_ID, roleIds);

        verify(userRoleMapper).delete(any());
        verify(userRoleMapper, times(roleIds.size())).insert(any(SysUserRole.class));
        verify(permissionCache).evictUser(USER_ID);
    }

    @Test
    @DisplayName("delete：用户存在时删除用户与关联并失效权限缓存")
    void shouldDeleteUserAndEvictPermissionCache() {
        when(userMapper.selectById(USER_ID)).thenReturn(user(1));
        when(userMapper.deleteById(USER_ID)).thenReturn(1);

        userService.delete(USER_ID);

        verify(userMapper).deleteById(USER_ID);
        verify(userRoleMapper).delete(any());
        verify(permissionCache).evictUser(USER_ID);
    }
}
