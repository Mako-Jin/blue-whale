package com.bluewhale.auth.domain.permission.aggregate;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link UserPermissionAttribute} 聚合不变量单元测试（AUTH-17 / T-DDD-9）。
 *
 * @author BlueWhale
 */
class UserPermissionAttributeTest {

    private static final long USER_ID = 2L;
    private static final long PERMISSION_ID = 10L;

    @Test
    @DisplayName("create：合法构造，ID 为空并回填各字段")
    void shouldCreate() {
        UserPermissionAttribute attribute = UserPermissionAttribute.create(USER_ID, PERMISSION_ID, "deptId", "100");

        assertNull(attribute.getId());
        assertEquals(USER_ID, attribute.getUserId());
        assertEquals(PERMISSION_ID, attribute.getPermissionId());
        assertEquals("deptId", attribute.getAttrKey());
        assertEquals("100", attribute.getAttrValue());
    }

    @Test
    @DisplayName("不变量：attrKey 为空抛参数错误")
    void shouldRejectBlankAttrKey() {
        BizException ex = assertThrows(BizException.class,
                () -> UserPermissionAttribute.create(USER_ID, PERMISSION_ID, " ", "100"));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("属性键不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("不变量：userId 与 permissionId 为空抛参数错误")
    void shouldRejectNullIds() {
        BizException userEx = assertThrows(BizException.class,
                () -> UserPermissionAttribute.create(null, PERMISSION_ID, "deptId", "100"));
        BizException permEx = assertThrows(BizException.class,
                () -> UserPermissionAttribute.create(USER_ID, null, "deptId", "100"));

        assertEquals("用户 ID 不能为空", userEx.getMessage());
        assertEquals("权限点 ID 不能为空", permEx.getMessage());
    }

    @Test
    @DisplayName("reconstitute：按持久化数据原样还原")
    void shouldReconstitute() {
        UserPermissionAttribute attribute = UserPermissionAttribute.reconstitute(
                7L, USER_ID, PERMISSION_ID, "deptId", "100");

        assertEquals(7L, attribute.getId());
        assertEquals(USER_ID, attribute.getUserId());
        assertEquals(PERMISSION_ID, attribute.getPermissionId());
        assertEquals("deptId", attribute.getAttrKey());
        assertEquals("100", attribute.getAttrValue());
    }
}
