package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.auth.entity.SysPermission;
import com.bluewhale.auth.entity.SysRolePermission;
import com.bluewhale.auth.entity.SysUserRole;
import com.bluewhale.auth.mapper.SysPermissionMapper;
import com.bluewhale.auth.mapper.SysRoleMapper;
import com.bluewhale.auth.mapper.SysRolePermissionMapper;
import com.bluewhale.auth.mapper.SysUserRoleMapper;
import com.bluewhale.common.tenant.TenantContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RoleRepositoryImpl} 用户→角色→权限查询链路单元测试（AUTH-17 / T-DDD-12）。
 *
 * <p>聚焦 {@code listRoleIdsByUserId}、{@code listPermissionIdsByRoleIds} 与
 * {@code listEnabledPermissionCodesByIds} 的 PO 映射与去重、空入参短路。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class RoleRepositoryImplTest {

    private static final long USER_ID = 5L;
    private static final long ROLE_ID = 7L;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private SysRolePermissionMapper rolePermissionMapper;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @Mock
    private SysPermissionMapper permissionMapper;

    @InjectMocks
    private RoleRepositoryImpl repository;

    @BeforeEach
    void bindTenant() {
        TenantContext.set(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * 注册实体元信息缓存：{@code LambdaQueryWrapper} 按实体解析列名，纯单测需手动初始化 TableInfo。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
        TableInfoHelper.initTableInfo(assistant, SysRolePermission.class);
        TableInfoHelper.initTableInfo(assistant, SysPermission.class);
    }

    private static SysUserRole userRole(long roleId) {
        SysUserRole po = new SysUserRole();
        po.setUserId(USER_ID);
        po.setRoleId(roleId);
        return po;
    }

    private static SysRolePermission rolePermission(long permissionId) {
        SysRolePermission po = new SysRolePermission();
        po.setRoleId(ROLE_ID);
        po.setPermissionId(permissionId);
        return po;
    }

    private static SysPermission permission(long id, String code, int status) {
        SysPermission po = new SysPermission();
        po.setId(id);
        po.setPermCode(code);
        po.setStatus(status);
        return po;
    }

    @Test
    @DisplayName("listRoleIdsByUserId：映射用户关联的角色 ID")
    void shouldListRoleIdsByUserId() {
        when(userRoleMapper.selectList(ArgumentMatchers.<Wrapper<SysUserRole>>any()))
                .thenReturn(List.of(userRole(1L), userRole(2L)));

        assertEquals(List.of(1L, 2L), repository.listRoleIdsByUserId(USER_ID));
        verify(userRoleMapper).selectList(ArgumentMatchers.<Wrapper<SysUserRole>>any());
    }

    @Test
    @DisplayName("listRoleIdsByUserId：无关联记录时返回空列表")
    void shouldReturnEmptyWhenUserHasNoRole() {
        when(userRoleMapper.selectList(ArgumentMatchers.<Wrapper<SysUserRole>>any()))
                .thenReturn(List.of());

        assertTrue(repository.listRoleIdsByUserId(USER_ID).isEmpty());
    }

    @Test
    @DisplayName("listPermissionIdsByRoleIds：空入参直接返回空列表且不查库")
    void shouldReturnEmptyWhenRoleIdsEmpty() {
        assertTrue(repository.listPermissionIdsByRoleIds(List.of()).isEmpty());
        verify(rolePermissionMapper, never()).selectList(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("listPermissionIdsByRoleIds：映射权限 ID 并按 ID 去重")
    void shouldListPermissionIdsDistinct() {
        when(rolePermissionMapper.selectList(ArgumentMatchers.<Wrapper<SysRolePermission>>any()))
                .thenReturn(List.of(rolePermission(10L), rolePermission(10L), rolePermission(11L)));

        assertEquals(List.of(10L, 11L), repository.listPermissionIdsByRoleIds(List.of(ROLE_ID)));
    }

    @Test
    @DisplayName("listEnabledPermissionCodesByIds：空入参直接返回空列表且不查库")
    void shouldReturnEmptyWhenPermissionIdsEmpty() {
        assertTrue(repository.listEnabledPermissionCodesByIds(List.of()).isEmpty());
        verify(permissionMapper, never()).selectList(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("listEnabledPermissionCodesByIds：映射启用权限码并去重")
    void shouldListEnabledPermissionCodesDistinct() {
        when(permissionMapper.selectList(ArgumentMatchers.<Wrapper<SysPermission>>any()))
                .thenReturn(List.of(permission(10L, "sys:user:list", 1),
                        permission(11L, "sys:user:list", 1),
                        permission(12L, "sys:role:list", 1)));

        assertEquals(List.of("sys:user:list", "sys:role:list"),
                repository.listEnabledPermissionCodesByIds(List.of(10L, 11L, 12L)));
    }
}
