package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.auth.dto.UpdateAttrsRequest;
import com.bluewhale.auth.dto.UserAttrItem;
import com.bluewhale.auth.entity.SysUserPermission;
import com.bluewhale.auth.mapper.SysUserPermissionMapper;
import com.bluewhale.auth.vo.UserAttrVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysUserPermissionServiceImpl} 单元测试：ABAC 属性查询与空属性全量覆盖（AUTH-23）。
 * <p>说明：`replaceUserAttrs` 的非空分支走 `saveBatch`，其 `getSqlStatement` 依赖真实 Mapper 代理，
 * 纯 Mock 单测无法构造（记入残余项，待容器就绪后由集成测试覆盖）。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SysUserPermissionServiceImplTest {

    private static final long USER_ID = 2L;
    private static final long PERMISSION_ID = 10L;

    @Mock
    private SysUserPermissionMapper userPermissionMapper;

    private SysUserPermissionServiceImpl userPermissionService;

    @BeforeEach
    void setUp() {
        userPermissionService = new SysUserPermissionServiceImpl();
        ReflectionTestUtils.setField(userPermissionService, "baseMapper", userPermissionMapper);
    }

    private static SysUserPermission row(Long permissionId, String key, String value) {
        SysUserPermission row = new SysUserPermission();
        row.setId(1L);
        row.setUserId(USER_ID);
        row.setPermissionId(permissionId);
        row.setAttrKey(key);
        row.setAttrValue(value);
        return row;
    }

    private static UpdateAttrsRequest attrsRequest(List<UserAttrItem> items) {
        UpdateAttrsRequest request = new UpdateAttrsRequest();
        request.setAttrs(items);
        return request;
    }

    @Test
    @DisplayName("listByUserId：返回用户 ABAC 属性视图列表")
    void shouldListByUserId() {
        when(userPermissionMapper.selectList(ArgumentMatchers.<Wrapper<SysUserPermission>>any()))
                .thenReturn(List.of(row(PERMISSION_ID, "deptId", "100")));

        List<UserAttrVO> attrs = userPermissionService.listByUserId(USER_ID);

        assertEquals(1, attrs.size());
        assertEquals("deptId", attrs.get(0).getAttrKey());
        assertEquals("100", attrs.get(0).getAttrValue());
    }

    @Test
    @DisplayName("replaceUserAttrs：属性列表为空时仅物理清理旧值")
    void shouldReplaceWithEmptyAttrs() {
        userPermissionService.replaceUserAttrs(USER_ID, attrsRequest(List.of()));

        verify(userPermissionMapper).delete(ArgumentMatchers.<Wrapper<SysUserPermission>>any());
    }
}
