package com.bluewhale.crm.marketing.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 触达明细回执状态机单元测试。
 *
 * @author BlueWhale
 */
class MarketingSendItemReceiptTest {

    /** 回执时间 */
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 8, 18, 0);

    /**
     * 构造指定状态的明细。
     *
     * @param status 初始状态
     * @return 明细
     */
    private static MarketingSendItem item(SendItemStatus status) {
        MarketingSendItem item = new MarketingSendItem();
        item.setStatus(status);
        return item;
    }

    @Test
    @DisplayName("SENT 可推进到 DELIVERED 并记录送达时间")
    void sentShouldAdvanceToDelivered() {
        MarketingSendItem item = item(SendItemStatus.SENT);
        assertTrue(item.applyReceipt(SendItemStatus.DELIVERED, AT, null));
        assertEquals(SendItemStatus.DELIVERED, item.getStatus());
        assertEquals(AT, item.getDeliveredAt());
        assertNull(item.getError());
    }

    @Test
    @DisplayName("DELIVERED 可推进到 READ，送达时间保留首次值")
    void deliveredShouldAdvanceToRead() {
        MarketingSendItem item = item(SendItemStatus.DELIVERED);
        LocalDateTime deliveredAt = AT.minusHours(1);
        item.setDeliveredAt(deliveredAt);
        assertTrue(item.applyReceipt(SendItemStatus.READ, AT, null));
        assertEquals(SendItemStatus.READ, item.getStatus());
        assertEquals(1, item.getReadFlag());
        assertEquals(deliveredAt, item.getDeliveredAt());
    }

    @Test
    @DisplayName("已读后迟到的 DELIVERED 不回退")
    void readShouldNotRegressToDelivered() {
        MarketingSendItem item = item(SendItemStatus.READ);
        assertFalse(item.applyReceipt(SendItemStatus.DELIVERED, AT, null));
        assertEquals(SendItemStatus.READ, item.getStatus());
    }

    @Test
    @DisplayName("未提交的明细不承接回执")
    void pendingShouldNotAcceptReceipt() {
        MarketingSendItem item = item(SendItemStatus.PENDING);
        assertFalse(item.applyReceipt(SendItemStatus.DELIVERED, AT, null));
        assertEquals(SendItemStatus.PENDING, item.getStatus());
    }

    @Test
    @DisplayName("BOUNCED 记录退信原因且为终态")
    void bouncedShouldKeepReasonAndBeTerminal() {
        MarketingSendItem item = item(SendItemStatus.SENT);
        assertTrue(item.applyReceipt(SendItemStatus.BOUNCED, AT, "550 mailbox not found"));
        assertEquals(SendItemStatus.BOUNCED, item.getStatus());
        assertEquals("550 mailbox not found", item.getError());
        assertFalse(item.applyReceipt(SendItemStatus.DELIVERED, AT, null));
    }

    @Test
    @DisplayName("已触达判定覆盖 SENT / DELIVERED / READ")
    void reachedShouldCoverReceiptStatuses() {
        assertTrue(SendItemStatus.SENT.isReached());
        assertTrue(SendItemStatus.DELIVERED.isReached());
        assertTrue(SendItemStatus.READ.isReached());
        assertFalse(SendItemStatus.PENDING.isReached());
        assertFalse(SendItemStatus.FAILED.isReached());
        assertFalse(SendItemStatus.BOUNCED.isReached());
    }
}
