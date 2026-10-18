package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.dto.PermissionRequest;
import com.bluewhale.auth.entity.SysPermission;
import com.bluewhale.auth.mapper.SysPermissionMapper;
import com.bluewhale.auth.security.PermissionCache;
import com.bluewhale.auth.vo.PermissionVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysPermissionServiceImpl} 单元测试：权限点分页/详情/增删改与编码校验（AUTH-23）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SysPermissionServiceImplTest {

    private static final long PERM_ID = 10L;

    @Mock
    private SysPermissionMapper permissionMapper;

    @Mock
    private PermissionCache permissionCache;

    private SysPermissionServiceImpl permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new SysPermissionServiceImpl(permissionCache);
        ReflectionTestUtils.setField(permissionService, "baseMapper", permissionMapper);
    }

    private static SysPermission permission(Long id, String code, String name) {
        SysPermission permission = new SysPermission();
        permission.setId(id);
        permission.setPermCode(code);
        permission.setPermName(name);
        permission.setPermType("API");
        permission.setStatus(1);
        return permission;
    }

    private static PermissionRequest request(String code) {
        PermissionRequest request = new PermissionRequest();
        request.setPermCode(code);
        request.setPermName("用户列表");
        request.setPermType("API");
        return request;
    }

    @Test
    @DisplayName("page：带关键字与类型时附加过滤条件并映射分页结果")
    void shouldPageWithFilters() {
        Page<SysPermission> page = new Page<>(1, 10);
        page.setRecords(List.of(permission(PERM_ID, "sys:user:list", "用户列表")));
        page.setTotal(1L);
        when(permissionMapper.selectPage(ArgumentMatchers.<Page<SysPermission>>any(),
                ArgumentMatchers.<Wrapper<SysPermission>>any())).thenReturn(page);

        PageResult<PermissionVO> result = permissionService.page(1, 10, "user", "API");

        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
        assertEquals("sys:user:list", result.getRecords().get(0).getPermCode());
    }

    @Test
    @DisplayName("page：无关键字与类型时仅分页查询")
    void shouldPageWithoutFilters() {
        Page<SysPermission> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(permissionMapper.selectPage(ArgumentMatchers.<Page<SysPermission>>any(),
                ArgumentMatchers.<Wrapper<SysPermission>>any())).thenReturn(page);

        PageResult<PermissionVO> result = permissionService.page(1, 10, null, null);

        assertEquals(0, result.getRecords().size());
    }

    @Test
    @DisplayName("listAllCodes：返回启用权限点的编码")
    void shouldListAllCodes() {
        when(permissionMapper.selectList(ArgumentMatchers.<Wrapper<SysPermission>>any()))
                .thenReturn(List.of(permission(PERM_ID, "sys:user:list", "用户列表")));

        List<String> codes = permissionService.listAllCodes();

        assertEquals(List.of("sys:user:list"), codes);
    }

    @Test
    @DisplayName("getDetail：命中时返回权限点视图对象")
    void shouldGetDetailWhenExists() {
        when(permissionMapper.selectById(PERM_ID)).thenReturn(permission(PERM_ID, "sys:user:list", "用户列表"));

        PermissionVO vo = permissionService.getDetail(PERM_ID);

        assertEquals(PERM_ID, vo.getId());
        assertEquals("sys:user:list", vo.getPermCode());
    }

    @Test
    @DisplayName("getDetail：不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectGetDetailWhenMissing() {
        when(permissionMapper.selectById(PERM_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> permissionService.getDetail(PERM_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("create：编码可用时落库并取默认父节点/排序/状态")
    void shouldCreateWhenCodeAvailable() {
        ArgumentCaptor<SysPermission> captor = ArgumentCaptor.forClass(SysPermission.class);
        when(permissionMapper.selectCount(ArgumentMatchers.<Wrapper<SysPermission>>any())).thenReturn(0L);

        permissionService.create(request("sys:user:list"));

        verify(permissionMapper).insert(captor.capture());
        assertEquals(0L, captor.getValue().getParentId());
        assertEquals(0, captor.getValue().getSortNo());
        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("create：编码已存在时抛 BizException 且不落库")
    void shouldRejectCreateWhenCodeExists() {
        when(permissionMapper.selectCount(ArgumentMatchers.<Wrapper<SysPermission>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> permissionService.create(request("sys:user:list")));
        verify(permissionMapper, never()).insert(any(SysPermission.class));
    }

    @Test
    @DisplayName("create：非法权限码由值对象前置校验拦截")
    void shouldRejectCreateWhenCodeIllegal() {
        assertThrows(BizException.class, () -> permissionService.create(request("BadCode")));
        verify(permissionMapper, never()).insert(any(SysPermission.class));
    }

    @Test
    @DisplayName("update：命中且编码不冲突时落库")
    void shouldUpdateWhenExists() {
        when(permissionMapper.selectById(PERM_ID)).thenReturn(permission(PERM_ID, "sys:user:list", "旧名"));
        when(permissionMapper.selectCount(ArgumentMatchers.<Wrapper<SysPermission>>any())).thenReturn(0L);

        permissionService.update(PERM_ID, request("sys:user:edit"));

        verify(permissionMapper).updateById(any(SysPermission.class));
        verify(permissionCache).evictByPermission(PERM_ID);
    }

    @Test
    @DisplayName("update：不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectUpdateWhenMissing() {
        when(permissionMapper.selectById(PERM_ID)).thenReturn(null);

        assertThrows(BizException.class, () -> permissionService.update(PERM_ID, request("sys:user:edit")));
        verify(permissionMapper, never()).updateById(any(SysPermission.class));
        verify(permissionCache, never()).evictByPermission(any());
    }

    @Test
    @DisplayName("update：编码已被其他权限占用时抛 BizException 且不落库、不失效")
    void shouldRejectUpdateWhenCodeDuplicated() {
        when(permissionMapper.selectById(PERM_ID)).thenReturn(permission(PERM_ID, "sys:user:list", "用户列表"));
        when(permissionMapper.selectCount(ArgumentMatchers.<Wrapper<SysPermission>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> permissionService.update(PERM_ID, request("sys:user:edit")));
        verify(permissionMapper, never()).updateById(any(SysPermission.class));
        verify(permissionCache, never()).evictByPermission(any());
    }

    @Test
    @DisplayName("delete：无子权限时逻辑删除")
    void shouldDeleteWhenNoChildren() {
        when(permissionMapper.selectCount(ArgumentMatchers.<Wrapper<SysPermission>>any())).thenReturn(0L);

        permissionService.delete(PERM_ID);

        verify(permissionMapper).deleteById(PERM_ID);
        verify(permissionCache).evictByPermission(PERM_ID);
    }

    @Test
    @DisplayName("delete：存在子权限时抛 BizException 且不删除、不失效")
    void shouldRejectDeleteWhenHasChildren() {
        when(permissionMapper.selectCount(ArgumentMatchers.<Wrapper<SysPermission>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> permissionService.delete(PERM_ID));
        verify(permissionMapper, never()).deleteById(anyLong());
        verify(permissionCache, never()).evictByPermission(any());
    }
}
