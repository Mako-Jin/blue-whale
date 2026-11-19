package com.bluewhale.message.support;

import com.bluewhale.message.dto.DeliveryReceiptItem;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.po.MessageDeliveryPo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FallbackReceiptMatcher} 兜底匹配单元测试。
 *
 * @author BlueWhale
 */
class FallbackReceiptMatcherTest {

    private static final String CHANNEL = "SMS";

    private static final String RECIPIENT = "13800000000";

    private static final LocalDateTime RECEIPT_TIME = LocalDateTime.of(2026, 11, 8, 18, 0);

    private final MessageDeliveryMapper deliveryMapper = mock(MessageDeliveryMapper.class);

    private final FallbackReceiptMatcher matcher = new FallbackReceiptMatcher(deliveryMapper);

    /**
     * 构造回执。
     *
     * @param recipient   接收人
     * @param receiptTime 回执时间
     * @return 回执项
     */
    private static DeliveryReceiptItem item(String recipient, LocalDateTime receiptTime) {
        DeliveryReceiptItem item = new DeliveryReceiptItem();
        item.setChannel(CHANNEL);
        item.setRecipient(recipient);
        item.setReceiptTime(receiptTime);
        item.setStatus("DELIVERED");
        return item;
    }

    /**
     * 构造投递记录。
     *
     * @param id         投递ID
     * @param recipient  接收人
     * @param sentAt     发送时间
     * @return 投递记录
     */
    private static MessageDeliveryPo delivery(long id, String recipient, LocalDateTime sentAt) {
        MessageDeliveryPo delivery = new MessageDeliveryPo();
        delivery.setId(id);
        delivery.setChannel(CHANNEL);
        delivery.setRecipient(recipient);
        delivery.setSentAt(sentAt);
        return delivery;
    }

    @Test
    @DisplayName("带 providerMessageId 的回执只走精确匹配，不参与兜底")
    void providerIdItemsShouldBeIgnored() {
        DeliveryReceiptItem item = item(RECIPIENT, RECEIPT_TIME);
        item.setProviderMessageId("p-1");

        assertTrue(matcher.match(List.of(item)).isEmpty());
        verify(deliveryMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("按渠道+接收人+5 分钟时间窗匹配时间最接近的一条")
    void shouldPickNearestWithinWindow() {
        DeliveryReceiptItem item = item(RECIPIENT, RECEIPT_TIME);
        when(deliveryMapper.selectList(any())).thenReturn(List.of(
                delivery(1L, RECIPIENT, RECEIPT_TIME.minusMinutes(3)),
                delivery(2L, RECIPIENT, RECEIPT_TIME.minusMinutes(1)),
                delivery(3L, "13900000000", RECEIPT_TIME.minusMinutes(1)),
                delivery(4L, RECIPIENT, RECEIPT_TIME.minusMinutes(10))));

        Map<DeliveryReceiptItem, List<MessageDeliveryPo>> result = matcher.match(List.of(item));

        assertEquals(1, result.size());
        assertEquals(2L, result.get(item).get(0).getId());
    }

    @Test
    @DisplayName("缺少接收人或回执时间时不兜底")
    void shouldSkipIncompleteItems() {
        DeliveryReceiptItem noRecipient = item(null, RECEIPT_TIME);
        DeliveryReceiptItem noTime = item(RECIPIENT, null);

        assertTrue(matcher.match(List.of(noRecipient, noTime)).isEmpty());
        verify(deliveryMapper, never()).selectList(any());
    }
}
