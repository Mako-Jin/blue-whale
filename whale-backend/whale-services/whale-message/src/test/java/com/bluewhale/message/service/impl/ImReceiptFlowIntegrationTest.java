package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.message.config.MessageReceiptProperties;
import com.bluewhale.message.dto.DeliveryReceiptItem;
import com.bluewhale.message.dto.DeliveryReceiptRequest;
import com.bluewhale.message.entity.MessageCallbackTask;
import com.bluewhale.message.entity.MessageDelivery;
import com.bluewhale.message.entity.MessageReceipt;
import com.bluewhale.message.entity.MessageRecord;
import com.bluewhale.message.mapper.MessageCallbackTaskMapper;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageReceiptMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.vo.ReceiptApplyResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IM 回执链路集成测试：以真实的回执受理、状态聚合与回调排队实现，
 * 模拟 whale-im 上报的 {@code clientMsgId} 回执，校验匹配、状态推进与回调入队。
 *
 * <p>覆盖链路后半段：whale-im → 消息服务回执接口 → 投递记录 → 逻辑消息状态 → 回调任务。</p>
 *
 * @author BlueWhale
 */
class ImReceiptFlowIntegrationTest {

    private static final Long TENANT_ID = 7L;

    private static final Long MESSAGE_ID = 88L;

    private static final String CLIENT_MSG_ID = "msg-88-456";

    private final MessageRecordMapper recordMapper = mock(MessageRecordMapper.class);

    private final MessageDeliveryMapper deliveryMapper = mock(MessageDeliveryMapper.class);

    private final MessageReceiptMapper receiptMapper = mock(MessageReceiptMapper.class);

    private final MessageCallbackTaskMapper callbackTaskMapper = mock(MessageCallbackTaskMapper.class);

    private final MessageReceiptProperties properties = new MessageReceiptProperties();

    private final MessageReceiptServiceImpl service = new MessageReceiptServiceImpl(recordMapper, deliveryMapper,
            receiptMapper, callbackTaskMapper, new ObjectMapper(), properties);

    /**
     * Lambda 条件构造器依赖实体表信息缓存，纯单测需手动初始化。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MessageRecord.class);
        TableInfoHelper.initTableInfo(assistant, MessageDelivery.class);
        TableInfoHelper.initTableInfo(assistant, MessageReceipt.class);
    }

    @BeforeEach
    void setUp() {
        when(receiptMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(receiptMapper.insert(any(MessageReceipt.class))).thenAnswer(invocation -> {
            MessageReceipt receipt = invocation.getArgument(0);
            receipt.setId(7001L);
            return 1;
        });
        when(callbackTaskMapper.insert(any(MessageCallbackTask.class))).thenReturn(1);
    }

    @Test
    @DisplayName("clientMsgId 回执命中 IM 投递：推进状态、聚合消息为 DELIVERED、回调入队")
    void shouldMatchImDeliveryAndQueueCallback() {
        MessageDelivery delivery = delivery(CLIENT_MSG_ID);
        MessageRecord record = record();
        when(deliveryMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(delivery)));
        when(recordMapper.selectById(MESSAGE_ID)).thenReturn(record);

        ReceiptApplyResult result = service.apply(request(CLIENT_MSG_ID, "DELIVERED"));

        assertEquals(1, result.getAccepted());
        assertEquals(1, result.getAdvanced());
        assertEquals(0, result.getUnmatched());
        assertEquals("DELIVERED", delivery.getStatus());
        assertEquals("DELIVERED", record.getStatus());
        verify(recordMapper).updateById(record);
        ArgumentCaptor<MessageCallbackTask> captor = ArgumentCaptor.forClass(MessageCallbackTask.class);
        verify(callbackTaskMapper).insert(captor.capture());
        MessageCallbackTask task = captor.getValue();
        assertEquals(MESSAGE_ID, task.getMessageId());
        assertTrue(task.getPayloadJson() != null && task.getPayloadJson().contains("\"DELIVERED\""));
    }

    @Test
    @DisplayName("已读回执按 clientMsgId 精确命中，不改动其它接收人的投递")
    void shouldMatchOnlyExactClientMsgId() {
        MessageDelivery delivery = delivery("msg-88-999");
        when(deliveryMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(delivery)));
        when(recordMapper.selectById(MESSAGE_ID)).thenReturn(record());

        ReceiptApplyResult result = service.apply(request(CLIENT_MSG_ID, "READ"));

        assertEquals(1, result.getAccepted());
        assertEquals(0, result.getAdvanced());
        assertEquals(1, result.getUnmatched());
        assertEquals("SENT", delivery.getStatus());
        verify(recordMapper, never()).updateById(any(MessageRecord.class));
        verify(callbackTaskMapper, never()).insert(any(MessageCallbackTask.class));
    }

    /**
     * 构造 IM 投递记录。
     *
     * @param providerMessageId 渠道侧标识
     * @return 投递记录
     */
    private static MessageDelivery delivery(String providerMessageId) {
        MessageDelivery delivery = new MessageDelivery();
        delivery.setId(5001L);
        delivery.setMessageId(MESSAGE_ID);
        delivery.setChannel("IM");
        delivery.setRecipient("456");
        delivery.setProviderMessageId(providerMessageId);
        delivery.setStatus("SENT");
        return delivery;
    }

    /**
     * 构造逻辑消息。
     *
     * @return 消息记录
     */
    private static MessageRecord record() {
        MessageRecord record = new MessageRecord();
        record.setId(MESSAGE_ID);
        record.setStatus("SENT");
        record.setCallbackUrl("https://example.com/message/callback");
        return record;
    }

    /**
     * 构造回执请求。
     *
     * @param providerMessageId 渠道侧标识
     * @param status            回执状态
     * @return 回执请求
     */
    private static DeliveryReceiptRequest request(String providerMessageId, String status) {
        DeliveryReceiptItem item = new DeliveryReceiptItem();
        item.setChannel("IM");
        item.setProviderMessageId(providerMessageId);
        item.setStatus(status);
        item.setReceiptId(providerMessageId + ":" + status);
        item.setReceiptTime(LocalDateTime.of(2026, 11, 19, 10, 0));
        DeliveryReceiptRequest request = new DeliveryReceiptRequest();
        request.setTenantId(TENANT_ID);
        request.setReceipts(List.of(item));
        return request;
    }
}
