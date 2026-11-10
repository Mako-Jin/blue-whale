package com.bluewhale.message.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.message.config.MessageReceiptProperties;
import com.bluewhale.message.dto.DeliveryReceiptItem;
import com.bluewhale.message.dto.DeliveryReceiptRequest;
import com.bluewhale.message.entity.MessageCallbackTask;
import com.bluewhale.message.entity.MessageDelivery;
import com.bluewhale.message.entity.MessageReceipt;
import com.bluewhale.message.entity.MessageRecord;
import com.bluewhale.message.enums.DeliveryStatus;
import com.bluewhale.message.mapper.MessageCallbackTaskMapper;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageReceiptMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.vo.ReceiptApplyResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageReceiptServiceImpl} 回执受理单元测试。
 *
 * <p>覆盖幂等、单向推进（含乱序回执）、退信错误留存、未匹配落库与回调排队。</p>
 *
 * @author BlueWhale
 */
class MessageReceiptServiceImplTest {

    /** 消息ID */
    private static final long MESSAGE_ID = 1001L;

    /** 投递记录ID */
    private static final long DELIVERY_ID = 2001L;

    /** 租户ID */
    private static final long TENANT_ID = 1L;

    private final MessageRecordMapper recordMapper = mock(MessageRecordMapper.class);

    private final MessageDeliveryMapper deliveryMapper = mock(MessageDeliveryMapper.class);

    private final MessageReceiptMapper receiptMapper = mock(MessageReceiptMapper.class);

    private final MessageCallbackTaskMapper callbackTaskMapper = mock(MessageCallbackTaskMapper.class);

    private final MessageReceiptProperties properties = new MessageReceiptProperties();

    /**
     * 构造被测服务。
     *
     * @return 回执受理服务
     */
    private MessageReceiptServiceImpl service() {
        when(receiptMapper.selectCount(any())).thenReturn(0L);
        when(receiptMapper.insert(any(MessageReceipt.class))).thenAnswer(invocation -> {
            MessageReceipt receipt = invocation.getArgument(0);
            receipt.setId(3001L);
            return 1;
        });
        when(callbackTaskMapper.insert(any(MessageCallbackTask.class))).thenReturn(1);
        return new MessageReceiptServiceImpl(recordMapper, deliveryMapper, receiptMapper,
                callbackTaskMapper, new ObjectMapper(), properties);
    }

    /**
     * 构造 SENT 状态的投递记录。
     *
     * @return 投递记录
     */
    private static MessageDelivery sentDelivery() {
        MessageDelivery delivery = new MessageDelivery();
        delivery.setId(DELIVERY_ID);
        delivery.setMessageId(MESSAGE_ID);
        delivery.setChannel("INAPP");
        delivery.setRecipient("101");
        delivery.setStatus(DeliveryStatus.SENT.name());
        delivery.setProviderMessageId("1001");
        return delivery;
    }

    /**
     * 构造带回调地址的逻辑消息。
     *
     * @param callbackUrl 回调地址
     * @return 消息记录
     */
    private static MessageRecord record(String callbackUrl) {
        MessageRecord record = new MessageRecord();
        record.setId(MESSAGE_ID);
        record.setBizType("CRM_MARKETING");
        record.setBizId("9001");
        record.setStatus("SENT");
        record.setCallbackUrl(callbackUrl);
        return record;
    }

    /**
     * 构造回执请求。
     *
     * @param status 回执状态
     * @param receiptId 回执ID
     * @return 请求
     */
    private static DeliveryReceiptRequest request(String status, String receiptId) {
        DeliveryReceiptItem item = new DeliveryReceiptItem();
        item.setChannel("INAPP");
        item.setReceiptId(receiptId);
        item.setProviderMessageId("1001");
        item.setStatus(status);
        item.setReceiptTime(LocalDateTime.of(2026, 11, 8, 18, 0));
        DeliveryReceiptRequest request = new DeliveryReceiptRequest();
        request.setTenantId(TENANT_ID);
        request.setReceipts(List.of(item));
        return request;
    }

    @Test
    @DisplayName("DELIVERED 回执推进投递状态并排队回调")
    void deliveredShouldAdvanceAndQueueCallback() {
        MessageDelivery delivery = sentDelivery();
        MessageRecord record = record("http://bw-crm:9007/crm/internal/marketing/delivery-callback");
        when(deliveryMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(delivery)));
        when(recordMapper.selectById(MESSAGE_ID)).thenReturn(record);
        when(deliveryMapper.updateById(any(MessageDelivery.class))).thenReturn(1);
        when(recordMapper.updateById(any(MessageRecord.class))).thenReturn(1);

        ReceiptApplyResult result = service().apply(request("DELIVERED", "rcpt-1"));

        assertEquals(1, result.getReceived());
        assertEquals(1, result.getAccepted());
        assertEquals(0, result.getDuplicated());
        assertEquals(0, result.getUnmatched());
        assertEquals(1, result.getAdvanced());
        assertEquals(1, result.getCallbackQueued());
        assertEquals(DeliveryStatus.DELIVERED.name(), delivery.getStatus());
        assertEquals(LocalDateTime.of(2026, 11, 8, 18, 0), delivery.getDeliveredAt());

        ArgumentCaptor<MessageCallbackTask> captor = ArgumentCaptor.forClass(MessageCallbackTask.class);
        verify(callbackTaskMapper).insert(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals(DELIVERY_ID, captor.getValue().getDeliveryId());
        assertTrue(captor.getValue().getPayloadJson().contains("\"DELIVERED\""));
    }

    @Test
    @DisplayName("重复回执幂等：不重复推进、不重复排队回调")
    void duplicatedReceiptShouldBeIgnored() {
        MessageReceiptServiceImpl service = service();
        when(receiptMapper.selectCount(any())).thenReturn(1L);
        ReceiptApplyResult result = service.apply(request("DELIVERED", "rcpt-1"));

        assertEquals(1, result.getDuplicated());
        assertEquals(0, result.getAccepted());
        assertEquals(0, result.getAdvanced());
        verify(deliveryMapper, never()).updateById(any(MessageDelivery.class));
        verify(callbackTaskMapper, never()).insert(any(MessageCallbackTask.class));
    }

    @Test
    @DisplayName("READ 可在 DELIVERED 之后推进，回填已读标记")
    void readShouldAdvanceFromDelivered() {
        MessageDelivery delivery = sentDelivery();
        delivery.setStatus(DeliveryStatus.DELIVERED.name());
        delivery.setDeliveredAt(LocalDateTime.of(2026, 11, 8, 17, 0));
        when(deliveryMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(delivery)));
        when(recordMapper.selectById(MESSAGE_ID)).thenReturn(record(null));
        when(deliveryMapper.updateById(any(MessageDelivery.class))).thenReturn(1);
        when(recordMapper.updateById(any(MessageRecord.class))).thenReturn(1);

        ReceiptApplyResult result = service().apply(request("READ", "rcpt-2"));

        assertEquals(1, result.getAdvanced());
        assertEquals(DeliveryStatus.READ.name(), delivery.getStatus());
        assertEquals(1, delivery.getReadFlag());
        assertEquals(LocalDateTime.of(2026, 11, 8, 18, 0), delivery.getReadAt());
        assertEquals(LocalDateTime.of(2026, 11, 8, 17, 0), delivery.getDeliveredAt());
        assertEquals(0, result.getCallbackQueued(), "未登记回调地址时不应排队回调");
    }

    @Test
    @DisplayName("乱序回执不回退：已读后的 DELIVERED 不生效")
    void lateDeliveredShouldNotRegressRead() {
        MessageDelivery delivery = sentDelivery();
        delivery.setStatus(DeliveryStatus.READ.name());
        when(deliveryMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(delivery)));
        when(recordMapper.selectById(MESSAGE_ID)).thenReturn(record(null));

        ReceiptApplyResult result = service().apply(request("DELIVERED", "rcpt-3"));

        assertEquals(0, result.getAdvanced());
        assertEquals(DeliveryStatus.READ.name(), delivery.getStatus());
        verify(deliveryMapper, never()).updateById(any(MessageDelivery.class));
    }

    @Test
    @DisplayName("BOUNCED 回执留存退信原因")
    void bouncedShouldKeepErrorReason() {
        MessageDelivery delivery = sentDelivery();
        when(deliveryMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(delivery)));
        when(recordMapper.selectById(MESSAGE_ID)).thenReturn(record(null));
        when(deliveryMapper.updateById(any(MessageDelivery.class))).thenReturn(1);
        when(recordMapper.updateById(any(MessageRecord.class))).thenReturn(1);
        DeliveryReceiptRequest request = request("BOUNCED", "rcpt-4");
        request.getReceipts().get(0).setErrorMessage("550 mailbox not found");

        ReceiptApplyResult result = service().apply(request);

        assertEquals(1, result.getAdvanced());
        assertEquals(DeliveryStatus.BOUNCED.name(), delivery.getStatus());
        assertEquals("550 mailbox not found", delivery.getErrorMessage());
    }

    @Test
    @DisplayName("未匹配到投递记录的回执照常落库并标记未匹配")
    void unmatchedReceiptShouldBeRecorded() {
        when(deliveryMapper.selectList(any())).thenReturn(new ArrayList<>());

        ReceiptApplyResult result = service().apply(request("DELIVERED", "rcpt-5"));

        assertEquals(1, result.getAccepted());
        assertEquals(1, result.getUnmatched());
        assertEquals(0, result.getAdvanced());
        ArgumentCaptor<MessageReceipt> captor = ArgumentCaptor.forClass(MessageReceipt.class);
        verify(receiptMapper).insert(captor.capture());
        assertEquals("UNMATCHED", captor.getValue().getMatchStatus());
        assertNull(captor.getValue().getDeliveryId());
    }

    @Test
    @DisplayName("非法回执状态直接拒绝")
    void invalidStatusShouldBeRejected() {
        MessageReceiptServiceImpl service = service();
        BizException exception = assertThrows(BizException.class,
                () -> service.apply(request("SENT", "rcpt-6")));
        assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("回执受理开关关闭时拒绝上报")
    void disabledReceiptShouldBeRejected() {
        properties.setEnabled(false);
        MessageReceiptServiceImpl service = new MessageReceiptServiceImpl(recordMapper, deliveryMapper,
                receiptMapper, callbackTaskMapper, new ObjectMapper(), properties);
        assertThrows(BizException.class, () -> service.apply(request("DELIVERED", "rcpt-7")));
    }

    @Test
    @DisplayName("回执全部送达后逻辑消息状态聚合为 DELIVERED")
    void recordStatusShouldAggregateToDelivered() {
        MessageDelivery delivery = sentDelivery();
        when(deliveryMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(delivery)));
        MessageRecord record = record(null);
        when(recordMapper.selectById(MESSAGE_ID)).thenReturn(record);
        when(deliveryMapper.updateById(any(MessageDelivery.class))).thenReturn(1);
        when(recordMapper.updateById(any(MessageRecord.class))).thenReturn(1);

        service().apply(request("DELIVERED", "rcpt-8"));

        assertEquals("DELIVERED", record.getStatus());
        assertFalse(record.getStatus().equals("SENT"));
    }
}
