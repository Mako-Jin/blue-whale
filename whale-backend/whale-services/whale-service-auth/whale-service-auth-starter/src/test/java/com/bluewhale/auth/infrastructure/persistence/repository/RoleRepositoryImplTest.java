package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.auth.infrastructure.persistence.entity.SysPermissionPo;
import com.bluewhale.auth.infrastructure.persistence.entity.SysRolePo;
import com.bluewhale.auth.infrastructure.persistence.entity.SysRolePermissionPo;
import com.bluewhale.auth.infrastructure.persistence.entity.SysUserRolePo;
import com.bluewhale.auth.infrastructure.persistence.entity.UserTenantRefPo;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysPermissionMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysRoleMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysRolePermissionMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserRoleMapper;
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
        TableInfoHelper.initTableInfo(assistant, SysUserRolePo.class);
        TableInfoHelper.initTableInfo(assistant, SysRolePermissionPo.class);
        TableInfoHelper.initTableInfo(assistant, SysPermissionPo.class);
        TableInfoHelper.initTableInfo(assistant, SysRolePo.class);
    }

    private static SysUserRolePo userRole(long roleId) {
        SysUserRolePo po = new SysUserRolePo();
        po.setUserId(USER_ID);
        po.setRoleId(roleId);
        return po;
    }

    private static SysRolePo role(long id) {
        SysRolePo po = new SysRolePo();
        po.setId(id);
        po.setTenantId(1L);
        return po;
    }

    private static SysRolePermissionPo rolePermission(long permissionId) {
        SysRolePermissionPo po = new SysRolePermissionPo();
        po.setRoleId(ROLE_ID);
        po.setPermissionId(permissionId);
        return po;
    }

    private static SysPermissionPo permission(long id, String code, int status) {
        SysPermissionPo po = new SysPermissionPo();
        po.setId(id);
        po.setPermCode(code);
        po.setStatus(status);
        return po;
    }

    @Test
    @DisplayName("listRoleIdsByUserId：映射用户关联的角色 ID")
    void shouldListRoleIdsByUserId() {
        when(roleMapper.selectList(ArgumentMatchers.<Wrapper<SysRolePo>>any()))
                .thenReturn(List.of(role(1L), role(2L)));
        when(userRoleMapper.selectList(ArgumentMatchers.<Wrapper<SysUserRolePo>>any()))
                .thenReturn(List.of(userRole(1L), userRole(2L)));

        assertEquals(List.of(1L, 2L), repository.listRoleIdsByUserId(USER_ID));
    }

    @Test
    @DisplayName("listRoleIdsByUserId：当前租户无角色时返回空列表且不查关联表")
    void shouldReturnEmptyWhenTenantHasNoRole() {
        when(roleMapper.selectList(ArgumentMatchers.<Wrapper<SysRolePo>>any()))
                .thenReturn(List.of());

        assertTrue(repository.listRoleIdsByUserId(USER_ID).isEmpty());
        verify(userRoleMapper, never()).selectList(ArgumentMatchers.any());
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
        when(roleMapper.selectList(ArgumentMatchers.<Wrapper<SysRolePo>>any()))
                .thenReturn(List.of(role(ROLE_ID)));
        when(rolePermissionMapper.selectList(ArgumentMatchers.<Wrapper<SysRolePermissionPo>>any()))
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
        when(permissionMapper.selectList(ArgumentMatchers.<Wrapper<SysPermissionPo>>any()))
                .thenReturn(List.of(permission(10L, "sys:user:list", 1),
                        permission(11L, "sys:user:list", 1),
                        permission(12L, "sys:role:list", 1)));

        assertEquals(List.of("sys:user:list", "sys:role:list"),
                repository.listEnabledPermissionCodesByIds(List.of(10L, 11L, 12L)));
    }

    @Test
    @DisplayName("listUsersByPermissionId：映射 JOIN 投影为（租户, 用户）对")
    void shouldListUsersByPermissionId() {
        UserTenantRefPo ref1 = new UserTenantRefPo();
        ref1.setTenantId(9L);
        ref1.setUserId(USER_ID);
        UserTenantRefPo ref2 = new UserTenantRefPo();
        ref2.setTenantId(9L);
        ref2.setUserId(6L);
        when(roleMapper.selectUserTenantRefsByPermissionId(10L)).thenReturn(List.of(ref1, ref2));

        List<RoleRepository.UserTenantRef> refs = repository.listUsersByPermissionId(10L);

        assertEquals(2, refs.size());
        assertEquals(9L, refs.get(0).tenantId());
        assertEquals(USER_ID, refs.get(0).userId());
        assertEquals(6L, refs.get(1).userId());
    }

    @Test
    @DisplayName("listUsersByPermissionId：权限点 ID 为空时返回空列表且不查库")
    void shouldReturnEmptyWhenListUsersByPermissionIdNull() {
        assertTrue(repository.listUsersByPermissionId(null).isEmpty());
        verify(roleMapper, never()).selectUserTenantRefsByPermissionId(ArgumentMatchers.any());
    }
}
