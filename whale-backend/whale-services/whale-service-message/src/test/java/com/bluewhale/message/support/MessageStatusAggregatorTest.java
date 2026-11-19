package com.bluewhale.message.support;

import com.bluewhale.message.entity.MessageDelivery;
import com.bluewhale.message.enums.MessageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MessageStatusAggregator} 聚合口径测试。
 *
 * <p>重点锁定回执推进后的状态不会被重试重算打回 SKIPPED（历史缺陷）。</p>
 *
 * @author BlueWhale
 */
class MessageStatusAggregatorTest {

    @Test
    @DisplayName("全部送达/已读聚合为 DELIVERED")
    void shouldAggregateDelivered() {
        assertEquals(MessageStatus.DELIVERED,
                MessageStatusAggregator.aggregate(List.of(delivery("DELIVERED"), delivery("READ"))));
    }

    @Test
    @DisplayName("送达与退信混合聚合为 PARTIAL")
    void shouldAggregateDeliveredWithBouncedAsPartial() {
        assertEquals(MessageStatus.PARTIAL,
                MessageStatusAggregator.aggregate(List.of(delivery("DELIVERED"), delivery("BOUNCED"))));
    }

    @Test
    @DisplayName("全部退信聚合为 FAILED")
    void shouldAggregateAllBouncedAsFailed() {
        assertEquals(MessageStatus.FAILED,
                MessageStatusAggregator.aggregate(List.of(delivery("BOUNCED"), delivery("FAILED"))));
    }

    @Test
    @DisplayName("仍有在途投递时不提前宣称送达")
    void shouldKeepSentWhenAnyPending() {
        assertEquals(MessageStatus.SENT,
                MessageStatusAggregator.aggregate(List.of(delivery("DELIVERED"), delivery("SENT"))));
    }

    @Test
    @DisplayName("送达 + 主动跳过聚合为 DELIVERED")
    void shouldTreatSkippedAsNormal() {
        assertEquals(MessageStatus.DELIVERED,
                MessageStatusAggregator.aggregate(List.of(delivery("DELIVERED"), delivery("SKIPPED"))));
    }

    @Test
    @DisplayName("全部 mock 聚合为 MOCKED，空投递聚合为 SKIPPED")
    void shouldAggregateMockedAndEmpty() {
        assertEquals(MessageStatus.MOCKED,
                MessageStatusAggregator.aggregate(List.of(delivery("MOCKED"), delivery("MOCKED"))));
        assertEquals(MessageStatus.SKIPPED, MessageStatusAggregator.aggregate(List.of()));
    }

    /**
     * 构造指定状态的投递记录。
     *
     * @param status 投递状态
     * @return 投递记录
     */
    private static MessageDelivery delivery(String status) {
        MessageDelivery delivery = new MessageDelivery();
        delivery.setStatus(status);
        return delivery;
    }
}
