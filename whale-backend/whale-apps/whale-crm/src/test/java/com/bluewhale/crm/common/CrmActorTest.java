package com.bluewhale.crm.common;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CrmActor} 单元测试：数据范围、明文权限与失败关闭。
 *
 * @author BlueWhale
 */
class CrmActorTest {

    @Test
    @DisplayName("全量数据范围：管理员 / 主管 / 分析角色可见全部客户")
    void shouldGrantAllDataScopeToManagerRoles() {
        assertTrue(actor(1L, "crm_sales_manager").allDataScope());
        assertTrue(actor(1L, "crm_admin").allDataScope());
        assertTrue(actor(1L, "crm_analyst").allDataScope());
        assertTrue(actor(1L, "tenant_admin").allDataScope());
        assertFalse(actor(1L, "crm_sales").allDataScope());
        assertFalse(actor(1L, "crm_service").allDataScope());
    }

    @Test
    @DisplayName("明文权限：仅持有敏感查看权限码时放行")
    void shouldCheckSensitivePermission() {
        CrmActor actor = new CrmActor(1L, "manager", Set.of("crm_sales_manager"),
                Set.of(CrmPermissions.SENSITIVE_VIEW));
        assertTrue(actor.canViewSensitive());
        assertFalse(actor(1L, "crm_sales").canViewSensitive());
    }

    @Test
    @DisplayName("缺少用户 ID：写操作失败关闭")
    void shouldFailFastWithoutUserId() {
        assertEquals(CrmErrorCode.ACTOR_REQUIRED,
                assertThrows(BizException.class, () -> CrmActor.anonymous().requireUserId()).getCode());
        assertEquals(CrmErrorCode.ACTOR_REQUIRED,
                assertThrows(BizException.class, () -> actor(0L, "crm_sales").requireUserId()).getCode());
        assertEquals(1L, actor(1L, "crm_sales").requireUserId());
    }

    @Test
    @DisplayName("同名角色判定兼容多角色与空角色")
    void shouldCheckRoles() {
        assertTrue(actor(1L, "crm_sales", "crm_sales_manager").hasRole("crm_sales_manager"));
        assertFalse(actor(1L, "crm_sales").hasRole("crm_analyst"));
        assertFalse(CrmActor.anonymous().hasRole((String[]) null));
    }

    private static CrmActor actor(Long userId, String... roles) {
        return new CrmActor(userId, "user", Set.of(roles), Set.of());
    }
}
