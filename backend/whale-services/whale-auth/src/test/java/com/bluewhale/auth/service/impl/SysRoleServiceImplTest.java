package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.dto.RoleCreateRequest;
import com.bluewhale.auth.dto.RoleUpdateRequest;
import com.bluewhale.auth.entity.SysPermission;
import com.bluewhale.auth.entity.SysRole;
import com.bluewhale.auth.entity.SysRolePermission;
import com.bluewhale.auth.entity.SysUserRole;
import com.bluewhale.auth.mapper.SysPermissionMapper;
import com.bluewhale.auth.mapper.SysRoleMapper;
import com.bluewhale.auth.mapper.SysRolePermissionMapper;
import com.bluewhale.auth.mapper.SysUserRoleMapper;
import com.bluewhale.auth.security.PermissionCache;
import com.bluewhale.auth.vo.RoleVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.lock.annotation.DistributedLock;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysRoleServiceImpl} 单元测试：角色分页/详情/增删改、权限分配与按用户查询角色/权限码（AUTH-23）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SysRoleServiceImplTest {

    private static final long ROLE_ID = 7L;
    private static final long USER_ID = 2L;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @Mock
    private SysRolePermissionMapper rolePermissionMapper;

    @Mock
    private SysPermissionMapper permissionMapper;

    @Mock
    private PermissionCache permissionCache;

    private SysRoleServiceImpl roleService;

    /**
     * 注册实体元信息缓存：`LambdaQueryWrapper#in` 会按实体解析列名，纯单测无 MyBatis 环境需手动初始化 TableInfo。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysRole.class);
        TableInfoHelper.initTableInfo(assistant, SysPermission.class);
        TableInfoHelper.initTableInfo(assistant, SysRolePermission.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
    }

    @BeforeEach
    void setUp() {
        roleService = new SysRoleServiceImpl(userRoleMapper, rolePermissionMapper, permissionMapper, permissionCache);
        ReflectionTestUtils.setField(roleService, "baseMapper", roleMapper);
    }

    private static SysRole role(Long id, String code, String name) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setSortNo(1);
        role.setStatus(1);
        return role;
    }

    private static RoleCreateRequest createRequest(String code) {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode(code);
        request.setRoleName("测试角色");
        return request;
    }

    @Test
    @DisplayName("page：带关键字时附加模糊条件并映射分页结果")
    void shouldPageWithKeyword() {
        Page<SysRole> page = new Page<>(1, 10);
        page.setRecords(List.of(role(ROLE_ID, "test_role", "测试角色")));
        page.setTotal(1L);
        when(roleMapper.selectPage(ArgumentMatchers.<Page<SysRole>>any(),
                ArgumentMatchers.<Wrapper<SysRole>>any())).thenReturn(page);

        PageResult<RoleVO> result = roleService.page(1, 10, "test");

        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
        assertEquals("test_role", result.getRecords().get(0).getRoleCode());
    }

    @Test
    @DisplayName("page：无关键字且无数据时返回空记录")
    void shouldPageWithoutKeywordAndEmpty() {
        Page<SysRole> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(roleMapper.selectPage(ArgumentMatchers.<Page<SysRole>>any(),
                ArgumentMatchers.<Wrapper<SysRole>>any())).thenReturn(page);

        PageResult<RoleVO> result = roleService.page(1, 10, "");

        assertEquals(0, result.getRecords().size());
        assertEquals(0L, result.getTotal());
    }

    @Test
    @DisplayName("getDetail：命中时返回角色视图对象")
    void shouldGetDetailWhenExists() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, "test_role", "测试角色"));

        RoleVO vo = roleService.getDetail(ROLE_ID);

        assertEquals(ROLE_ID, vo.getId());
        assertEquals("test_role", vo.getRoleCode());
    }

    @Test
    @DisplayName("getDetail：不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectGetDetailWhenMissing() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> roleService.getDetail(ROLE_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("create：编码可用时落库并取默认排序/状态")
    void shouldCreateWhenCodeAvailable() {
        ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
        when(roleMapper.selectCount(ArgumentMatchers.<Wrapper<SysRole>>any())).thenReturn(0L);

        RoleVO vo = roleService.create(createRequest("test_role"));

        verify(roleMapper).insert(captor.capture());
        assertEquals("test_role", vo.getRoleCode());
        assertEquals(0, captor.getValue().getSortNo());
        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("create：编码已存在时抛 BizException 且不落库")
    void shouldRejectCreateWhenCodeExists() {
        when(roleMapper.selectCount(ArgumentMatchers.<Wrapper<SysRole>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> roleService.create(createRequest("test_role")));
        verify(roleMapper, never()).insert(any(SysRole.class));
    }

    @Test
    @DisplayName("create：非法编码由值对象前置校验拦截")
    void shouldRejectCreateWhenCodeIllegal() {
        assertThrows(BizException.class, () -> roleService.create(createRequest("1BadCode")));
        verify(roleMapper, never()).insert(any(SysRole.class));
    }

    @Test
    @DisplayName("update：命中时按请求覆盖可空字段并落库")
    void shouldUpdateWhenExists() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, "test_role", "旧名"));
        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRoleName("新名");
        request.setDescription("描述");
        request.setSortNo(9);
        request.setStatus(0);

        RoleVO vo = roleService.update(ROLE_ID, request);

        assertEquals("新名", vo.getRoleName());
        assertEquals(9, vo.getSortNo());
        assertEquals(0, vo.getStatus());
        verify(roleMapper).updateById(any(SysRole.class));
        verify(permissionCache, never()).evictByRole(any());
    }

    @Test
    @DisplayName("update：不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectUpdateWhenMissing() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(null);
        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRoleName("新名");

        assertThrows(BizException.class, () -> roleService.update(ROLE_ID, request));
        verify(roleMapper, never()).updateById(any(SysRole.class));
        verify(permissionCache, never()).evictByRole(any());
    }

    @Test
    @DisplayName("delete：命中时逻辑删除角色并物理清理角色-权限关联")
    void shouldDeleteAndCleanRelations() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, "test_role", "测试角色"));

        roleService.delete(ROLE_ID);

        verify(roleMapper).deleteById(ROLE_ID);
        verify(rolePermissionMapper).delete(ArgumentMatchers.<Wrapper<SysRolePermission>>any());
        verify(permissionCache).evictByRole(ROLE_ID);
    }

    @Test
    @DisplayName("delete：不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectDeleteWhenMissing() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(null);

        assertThrows(BizException.class, () -> roleService.delete(ROLE_ID));
        verify(roleMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("assignPermissions：空权限列表时仅清理既有关联")
    void shouldAssignEmptyPermissions() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, "test_role", "测试角色"));

        roleService.assignPermissions(ROLE_ID, List.of());

        verify(rolePermissionMapper).delete(ArgumentMatchers.<Wrapper<SysRolePermission>>any());
        verify(rolePermissionMapper, never()).insert(any(SysRolePermission.class));
        verify(permissionCache).evictByRole(ROLE_ID);
    }

    @Test
    @DisplayName("assignPermissions：权限 ID 全部有效时逐条重建关联")
    void shouldAssignValidPermissions() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, "test_role", "测试角色"));
        when(permissionMapper.selectCount(ArgumentMatchers.<Wrapper<SysPermission>>any())).thenReturn(2L);

        roleService.assignPermissions(ROLE_ID, List.of(10L, 11L));

        verify(rolePermissionMapper).delete(ArgumentMatchers.<Wrapper<SysRolePermission>>any());
        verify(rolePermissionMapper, times(2)).insert(any(SysRolePermission.class));
        verify(permissionCache).evictByRole(ROLE_ID);
    }

    @Test
    @DisplayName("assignPermissions：存在无效权限 ID 时抛 BizException 且不写关联")
    void shouldRejectInvalidPermissions() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, "test_role", "测试角色"));
        when(permissionMapper.selectCount(ArgumentMatchers.<Wrapper<SysPermission>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> roleService.assignPermissions(ROLE_ID, List.of(10L, 11L)));
        verify(rolePermissionMapper, never()).insert(any(SysRolePermission.class));
    }

    @Test
    @DisplayName("assignPermissions：角色不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectAssignWhenRoleMissing() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(null);

        assertThrows(BizException.class, () -> roleService.assignPermissions(ROLE_ID, List.of(10L)));
        verify(rolePermissionMapper, never()).delete(ArgumentMatchers.<Wrapper<SysRolePermission>>any());
    }

    @Test
    @DisplayName("assignPermissions：方法上 @DistributedLock 接线正确（biz/key/failFast）")
    void shouldAnnotateAssignPermissionsWithDistributedLock() throws NoSuchMethodException {
        Method method = SysRoleServiceImpl.class.getMethod("assignPermissions", Long.class, List.class);
        DistributedLock annotation = method.getAnnotation(DistributedLock.class);

        assertNotNull(annotation);
        assertEquals("lock", annotation.prefix());
        assertEquals("auth:role", annotation.biz());
        assertEquals("#id", annotation.key());
        assertEquals(1L, annotation.waitTime());
        assertEquals(30L, annotation.leaseTime());
        assertTrue(annotation.failFast());
    }

    @Test
    @DisplayName("listPermissionIds：返回角色关联的权限 ID")
    void shouldListPermissionIds() {
        SysRolePermission relation = new SysRolePermission();
        relation.setRoleId(ROLE_ID);
        relation.setPermissionId(10L);
        when(rolePermissionMapper.selectList(ArgumentMatchers.<Wrapper<SysRolePermission>>any()))
                .thenReturn(List.of(relation));

        List<Long> ids = roleService.listPermissionIds(ROLE_ID);

        assertEquals(List.of(10L), ids);
    }

    @Test
    @DisplayName("listRoleCodesByUserId：用户无角色时返回空列表")
    void shouldListRoleCodesWhenNoRole() {
        when(userRoleMapper.selectList(ArgumentMatchers.<Wrapper<SysUserRole>>any())).thenReturn(List.of());

        List<String> codes = roleService.listRoleCodesByUserId(USER_ID);

        assertEquals(0, codes.size());
    }

    @Test
    @DisplayName("listRoleCodesByUserId：按启用状态过滤并返回角色编码")
    void shouldListRoleCodesWhenHasRole() {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(USER_ID);
        relation.setRoleId(ROLE_ID);
        when(userRoleMapper.selectList(ArgumentMatchers.<Wrapper<SysUserRole>>any()))
                .thenReturn(List.of(relation));
        when(roleMapper.selectList(ArgumentMatchers.<Wrapper<SysRole>>any()))
                .thenReturn(List.of(role(ROLE_ID, "test_role", "测试角色")));

        List<String> codes = roleService.listRoleCodesByUserId(USER_ID);

        assertEquals(List.of("test_role"), codes);
    }

    @Test
    @DisplayName("listPermissionCodesByUserId：用户无角色时返回空列表")
    void shouldListPermissionCodesWhenNoRole() {
        when(userRoleMapper.selectList(ArgumentMatchers.<Wrapper<SysUserRole>>any())).thenReturn(List.of());

        List<String> codes = roleService.listPermissionCodesByUserId(USER_ID);

        assertEquals(0, codes.size());
    }

    @Test
    @DisplayName("listPermissionCodesByUserId：角色无权限关联时返回空列表")
    void shouldListPermissionCodesWhenNoRelation() {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(USER_ID);
        relation.setRoleId(ROLE_ID);
        when(userRoleMapper.selectList(ArgumentMatchers.<Wrapper<SysUserRole>>any()))
                .thenReturn(List.of(relation));
        when(rolePermissionMapper.selectList(ArgumentMatchers.<Wrapper<SysRolePermission>>any()))
                .thenReturn(List.of());

        List<String> codes = roleService.listPermissionCodesByUserId(USER_ID);

        assertEquals(0, codes.size());
    }

    @Test
    @DisplayName("listPermissionCodesByUserId：正常返回去重后的权限码")
    void shouldListPermissionCodesNormal() {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(USER_ID);
        relation.setRoleId(ROLE_ID);
        SysRolePermission rolePerm = new SysRolePermission();
        rolePerm.setRoleId(ROLE_ID);
        rolePerm.setPermissionId(10L);
        SysPermission permission = new SysPermission();
        permission.setId(10L);
        permission.setPermCode("sys:user:list");
        permission.setStatus(1);
        when(userRoleMapper.selectList(ArgumentMatchers.<Wrapper<SysUserRole>>any()))
                .thenReturn(List.of(relation));
        when(rolePermissionMapper.selectList(ArgumentMatchers.<Wrapper<SysRolePermission>>any()))
                .thenReturn(List.of(rolePerm));
        when(permissionMapper.selectList(ArgumentMatchers.<Wrapper<SysPermission>>any()))
                .thenReturn(List.of(permission));

        List<String> codes = roleService.listPermissionCodesByUserId(USER_ID);

        assertEquals(List.of("sys:user:list"), codes);
    }
}
