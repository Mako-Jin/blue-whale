package com.bluewhale.file.domain.file.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link FileAssetDomainEvent} 单元测试：载荷完整性与变更类型约束。
 *
 * @author BlueWhale
 */
class FileAssetDomainEventTest {

    @Test
    @DisplayName("事件完整保留文件标识、租户与操作人")
    void shouldCarryAllFields() {
        FileAssetDomainEvent event = new FileAssetDomainEvent(7L, FileAssetDomainEvent.ChangeType.RESTORED,
                1L, "bluewhale", "tenants/1/2026/10/24/a.txt", 9L, 8L);

        assertEquals(7L, event.fileId());
        assertEquals(FileAssetDomainEvent.ChangeType.RESTORED, event.changeType());
        assertEquals(1L, event.tenantId());
        assertEquals("bluewhale", event.bucket());
        assertEquals("tenants/1/2026/10/24/a.txt", event.objectKey());
        assertEquals(9L, event.uploaderId());
        assertEquals(8L, event.operatorId());
    }

    @Test
    @DisplayName("变更类型为空时拒绝构造")
    void shouldRejectNullChangeType() {
        assertThrows(IllegalArgumentException.class,
                () -> new FileAssetDomainEvent(7L, null, 1L, "bluewhale", "tenants/1/a.txt", 9L, 9L));
    }
}
