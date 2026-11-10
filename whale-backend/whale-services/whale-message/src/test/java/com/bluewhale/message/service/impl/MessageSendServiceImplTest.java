package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.message.channel.ChannelMessage;
import com.bluewhale.message.channel.ChannelResult;
import com.bluewhale.message.channel.MessageChannelSender;
import com.bluewhale.message.config.MessageChannelProperties;
import com.bluewhale.message.dto.MessageRecipient;
import com.bluewhale.message.dto.SendMessageRequest;
import com.bluewhale.message.entity.MessageDelivery;
import com.bluewhale.message.entity.MessageRecord;
import com.bluewhale.message.entity.MessageTemplate;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.mapper.MessageTemplateMapper;
import com.bluewhale.message.vo.SendMessageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageSendServiceImpl} 发送内核单元测试。
 *
 * @author BlueWhale
 */
class MessageSendServiceImplTest {

    private final MessageRecordMapper recordMapper = mock(MessageRecordMapper.class);

    private final MessageDeliveryMapper deliveryMapper = mock(MessageDeliveryMapper.class);

    private final MessageTemplateMapper templateMapper = mock(MessageTemplateMapper.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MessageChannelProperties properties = new MessageChannelProperties();

    /**
     * 构造带指定渠道适配器的发送服务。
     *
     * @param senders 渠道适配器
     * @return 发送服务
     */
    private MessageSendServiceImpl service(MessageChannelSender... senders) {
        stubInsert();
        return new MessageSendServiceImpl(recordMapper, deliveryMapper, templateMapper,
                objectMapper, properties, List.of(senders));
    }

    /**
     * 模拟 MyBatis-Plus 插入回填主键。
     */
    private void stubInsert() {
        AtomicInteger deliveryId = new AtomicInteger(2000);
        doAnswer(invocation -> {
            MessageRecord record = invocation.getArgument(0);
            record.setId(1001L);
            return 1;
        }).when(recordMapper).insert(any(MessageRecord.class));
        doAnswer(invocation -> {
            MessageDelivery delivery = invocation.getArgument(0);
            delivery.setId((long) deliveryId.incrementAndGet());
            return 1;
        }).when(deliveryMapper).insert(any(MessageDelivery.class));
        when(recordMapper.updateById(any(MessageRecord.class))).thenReturn(1);
    }

    @Test
    @DisplayName("模板渲染生效，INAPP+SMS 聚合为 SENT")
    void shouldRenderTemplateAndAggregateStatus() {
        MessageTemplate template = new MessageTemplate();
        template.setTemplateCode("SYSTEM_NOTICE");
        template.setTitleTemplate("通知-${name}");
        template.setContentTemplate("内容-${name}");
        template.setStatus(1);
        when(templateMapper.selectOne(any(Wrapper.class))).thenReturn(template);

        MessageChannelSender inapp = sender(MessageChannel.INAPP, ChannelResult.sent("1001"));
        MessageChannelSender sms = sender(MessageChannel.SMS, ChannelResult.mocked());
        SendMessageRequest request = request(List.of("INAPP", "SMS"), "SYSTEM_NOTICE");

        SendMessageResult result = service(inapp, sms).send(request);

        assertEquals("SENT", result.getStatus());
        assertEquals(2, result.getDeliveries().size());
        ArgumentCaptor<MessageRecord> captor = ArgumentCaptor.forClass(MessageRecord.class);
        verify(recordMapper).insert(captor.capture());
        MessageRecord inserted = captor.getValue();
        assertEquals("通知-蓝鲸", inserted.getTitle());
        assertEquals("内容-蓝鲸", inserted.getContent());
        assertEquals("INAPP,SMS", inserted.getChannels());
        assertEquals("SENT", inserted.getStatus());
    }

    @Test
    @DisplayName("幂等命中返回既有消息且不重复插入")
    void shouldReturnExistingOnIdempotencyHit() {
        MessageRecord existing = new MessageRecord();
        existing.setId(9L);
        existing.setStatus("SENT");
        existing.setIdempotencyKey("k-1");
        MessageDelivery delivery = new MessageDelivery();
        delivery.setId(99L);
        delivery.setStatus("SENT");
        when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(deliveryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(delivery));
        SendMessageRequest request = request(List.of("INAPP"), null);
        request.setIdempotencyKey("k-1");

        SendMessageResult result = service(sender(MessageChannel.INAPP, ChannelResult.sent("9"))).send(request);

        assertTrue(result.isDuplicate());
        assertEquals(9L, result.getMessageId());
        assertEquals(1, result.getDeliveries().size());
        verify(recordMapper, never()).insert(any(MessageRecord.class));
    }

    @Test
    @DisplayName("单渠道失败不阻断其它渠道，状态为 PARTIAL")
    void shouldIsolateChannelFailure() {
        MessageChannelSender inapp = sender(MessageChannel.INAPP, ChannelResult.sent("1001"));
        MessageChannelSender sms = sender(MessageChannel.SMS, ChannelResult.failed("网关超时"));

        SendMessageResult result = service(inapp, sms).send(request(List.of("INAPP", "SMS"), null));

        assertEquals("PARTIAL", result.getStatus());
        assertEquals(2, result.getDeliveries().size());
        assertEquals("SENT", result.getDeliveries().get(0).getStatus());
        assertEquals("FAILED", result.getDeliveries().get(1).getStatus());
    }

    @Test
    @DisplayName("外部渠道全部 mock 时状态为 MOCKED")
    void shouldMarkMockedWhenAllChannelsMock() {
        MessageChannelSender email = sender(MessageChannel.EMAIL, ChannelResult.mocked());
        MessageChannelSender im = sender(MessageChannel.IM, ChannelResult.mocked());

        SendMessageResult result = service(email, im).send(request(List.of("EMAIL", "IM"), null));

        assertEquals("MOCKED", result.getStatus());
        assertFalse(result.isDuplicate());
        assertEquals(2, result.getDeliveries().size());
        assertTrue(result.getDeliveries().stream().allMatch(d -> "MOCKED".equals(d.getStatus())));
    }

    @Test
    @DisplayName("未注册渠道适配器时记为 SKIPPED，不影响已注册渠道")
    void shouldSkipChannelWithoutSender() {
        SendMessageResult result = service(sender(MessageChannel.INAPP, ChannelResult.sent("1001")))
                .send(request(List.of("INAPP", "SMS"), null));

        assertEquals("SENT", result.getStatus());
        assertEquals(2, result.getDeliveries().size());
        assertEquals("SKIPPED", result.getDeliveries().get(1).getStatus());
    }

    @Test
    @DisplayName("mock 模式下拒绝重试，避免假恢复")
    void shouldRejectRetryInMockMode() {
        properties.setMock(true);
        MessageRecord record = new MessageRecord();
        record.setId(1001L);
        when(recordMapper.selectById(1001L)).thenReturn(record);

        assertThrows(BizException.class, () -> service().retry(1001L));
        verify(deliveryMapper, never()).updateById(any(MessageDelivery.class));
    }

    @Test
    @DisplayName("没有失败投递时拒绝重试")
    void shouldRejectRetryWithoutFailedDelivery() {
        properties.setMock(false);
        MessageRecord record = new MessageRecord();
        record.setId(1001L);
        record.setStatus("SENT");
        when(recordMapper.selectById(1001L)).thenReturn(record);
        MessageDelivery delivery = new MessageDelivery();
        delivery.setId(2001L);
        delivery.setMessageId(1001L);
        delivery.setStatus("SENT");
        when(deliveryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(delivery));

        assertThrows(BizException.class, () -> service().retry(1001L));
    }

    @Test
    @DisplayName("重试失败投递后回写状态与尝试次数")
    void shouldRetryFailedDelivery() {
        properties.setMock(false);
        MessageRecord record = new MessageRecord();
        record.setId(1001L);
        record.setStatus("PARTIAL");
        record.setPayloadJson("{\"name\":\"蓝鲸\"}");
        when(recordMapper.selectById(1001L)).thenReturn(record);
        MessageDelivery failed = new MessageDelivery();
        failed.setId(2001L);
        failed.setMessageId(1001L);
        failed.setChannel("EMAIL");
        failed.setRecipient("user@example.com");
        failed.setStatus("FAILED");
        failed.setErrorMessage("网关超时");
        failed.setAttempts(1);
        when(deliveryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(failed));

        SendMessageResult result = service(sender(MessageChannel.EMAIL, ChannelResult.sent("p-1")))
                .retry(1001L);

        assertEquals("SENT", result.getStatus());
        assertEquals("SENT", failed.getStatus());
        assertEquals(2, failed.getAttempts());
        verify(deliveryMapper).updateById(eq(failed));
    }

    /**
     * 构造渠道适配器 mock。
     *
     * @param channel 渠道
     * @param result  固定返回结果
     * @return 适配器 mock
     */
    private static MessageChannelSender sender(MessageChannel channel, ChannelResult result) {
        MessageChannelSender sender = mock(MessageChannelSender.class);
        when(sender.channel()).thenReturn(channel);
        when(sender.send(any(ChannelMessage.class))).thenReturn(result);
        return sender;
    }

    /**
     * 构造发送请求。
     *
     * @param channels     渠道
     * @param templateCode 模板编码
     * @return 请求
     */
    private static SendMessageRequest request(List<String> channels, String templateCode) {
        MessageRecipient recipient = new MessageRecipient();
        recipient.setUserId(1001L);
        recipient.setEmail("user@example.com");
        recipient.setPhone("13800000000");

        SendMessageRequest request = new SendMessageRequest();
        request.setTenantId(1L);
        request.setBizType("PM_TASK");
        request.setBizId("T-1001");
        request.setTemplateCode(templateCode);
        request.setPayload(Map.of("name", "蓝鲸"));
        request.setChannels(channels);
        request.setRecipients(List.of(recipient));
        return request;
    }
}
