package com.bluewhale.auth.domain.event;

import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 领域事件类型单元测试：sealed 收敛、字段可读与枚举齐全（AUTH-17 / T-DDD-11）。
 *
 * @author BlueWhale
 */
class AuthDomainEventTest {

    @Test
    @DisplayName("六类事件均可构造且是 AuthDomainEvent 的合法实现")
    void shouldConstructAllPermittedEvents() {
        assertInstanceOf(AuthDomainEvent.class, new UserDomainEvent(1L, ChangeType.CREATED));
        assertInstanceOf(AuthDomainEvent.class, new RoleDomainEvent(2L, ChangeType.UPDATED));
        assertInstanceOf(AuthDomainEvent.class, new PermissionDomainEvent(3L, ChangeType.DELETED));
        assertInstanceOf(AuthDomainEvent.class, new MenuDomainEvent(4L, ChangeType.CREATED));
        assertInstanceOf(AuthDomainEvent.class, new DeptDomainEvent(5L, ChangeType.UPDATED));
        assertInstanceOf(AuthDomainEvent.class,
                new UserPermissionDomainEvent(6L, ChangeType.ATTRS_REPLACED));
    }

    @Test
    @DisplayName("事件字段可读：聚合 ID 允许为空，变更类型原样保留")
    void shouldExposeRecordComponents() {
        UserDomainEvent event = new UserDomainEvent(null, ChangeType.PASSWORD_CHANGED);

        assertNull(event.aggregateId());
        assertEquals(ChangeType.PASSWORD_CHANGED, event.changeType());
    }

    @Test
    @DisplayName("ChangeType：枚举值齐全且顺序稳定")
    void shouldContainAllChangeTypes() {
        assertEquals(8, ChangeType.values().length);
        assertEquals(ChangeType.CREATED, ChangeType.valueOf("CREATED"));
        assertEquals(ChangeType.UPDATED, ChangeType.valueOf("UPDATED"));
        assertEquals(ChangeType.DELETED, ChangeType.valueOf("DELETED"));
        assertEquals(ChangeType.STATUS_CHANGED, ChangeType.valueOf("STATUS_CHANGED"));
        assertEquals(ChangeType.PASSWORD_CHANGED, ChangeType.valueOf("PASSWORD_CHANGED"));
        assertEquals(ChangeType.ROLES_ASSIGNED, ChangeType.valueOf("ROLES_ASSIGNED"));
        assertEquals(ChangeType.PERMISSIONS_ASSIGNED, ChangeType.valueOf("PERMISSIONS_ASSIGNED"));
        assertEquals(ChangeType.ATTRS_REPLACED, ChangeType.valueOf("ATTRS_REPLACED"));
    }
}
