package com.bluewhale.audit.mq.support;

import com.bluewhale.common.audit.properties.AuditMqProperties;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 死信重放单测：原样重投后确认、重投失败回退死信队列、空队列提前结束。
 *
 * @author BlueWhale
 */
class AuditDlqReplayServiceTest {

    private static final String EXCHANGE = "bluewhale.audit.exchange";

    private static final String ROUTING_KEY = "audit.log";

    private static final String DLQ = "bluewhale.audit.dlq";

    private static final long DELIVERY_TAG = 7L;

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    private final Channel channel = mock(Channel.class);

    private final AuditMqProperties properties = new AuditMqProperties();

    private final AuditDlqReplayService service = new AuditDlqReplayService(rabbitTemplate, properties);

    @Test
    void shouldRepublishThenAckMessage() throws Exception {
        GetResponse response = response();
        when(channel.basicGet(DLQ, false)).thenReturn(response, (GetResponse) null);
        stubExecute();

        int replayed = service.replay(10);

        assertThat(replayed).isEqualTo(1);
        verify(channel).basicPublish(eq(EXCHANGE), eq(ROUTING_KEY), any(), any(byte[].class));
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, times(2)).basicGet(DLQ, false);
    }

    @Test
    void shouldRequeueMessageWhenRepublishFails() throws Exception {
        GetResponse response = response();
        when(channel.basicGet(DLQ, false)).thenReturn(response);
        doThrow(new IOException("broker down")).when(channel)
                .basicPublish(anyString(), anyString(), any(), any(byte[].class));
        stubExecute();

        assertThatThrownBy(() -> service.replay(1)).isInstanceOf(IOException.class);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, times(0)).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void shouldReturnZeroWhenDeadLetterQueueIsEmpty() throws Exception {
        when(channel.basicGet(DLQ, false)).thenReturn(null);
        stubExecute();

        int replayed = service.replay(50);

        assertThat(replayed).isZero();
        verify(channel).basicGet(DLQ, false);
    }

    /**
     * 让 RabbitTemplate.execute 直接在当前线程执行回调
     */
    private void stubExecute() {
        doAnswer(invocation -> {
            org.springframework.amqp.rabbit.core.ChannelCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(channel);
        }).when(rabbitTemplate).execute(any());
    }

    private static GetResponse response() {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().messageId("m-1").build();
        Envelope envelope = new Envelope(DELIVERY_TAG, false, EXCHANGE, ROUTING_KEY);
        return new GetResponse(envelope, props, "{}".getBytes(), 0);
    }
}
