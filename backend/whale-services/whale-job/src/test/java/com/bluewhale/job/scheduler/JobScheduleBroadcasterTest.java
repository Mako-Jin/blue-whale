package com.bluewhale.job.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobScheduleBroadcaster} 多实例失效广播单元测试。
 *
 * @author BlueWhale
 */
class JobScheduleBroadcasterTest {

    private static final int LISTENER_ID = 7;

    private static final long JOB_ID = 42L;

    private final RedissonClient redissonClient = mock(RedissonClient.class);

    private final RTopic topic = mock(RTopic.class);

    /** 收到的本地调度变更（[0]=jobId, [1]=reschedule） */
    private final List<Object[]> events = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private JobScheduleBroadcaster newBroadcaster(boolean redisAvailable) {
        ObjectProvider<RedissonClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisAvailable ? redissonClient : null);
        JobScheduleBroadcaster broadcaster = new JobScheduleBroadcaster(provider);
        broadcaster.registerConsumer((jobId, reschedule) -> events.add(new Object[]{jobId, reschedule}));
        if (redisAvailable) {
            when(redissonClient.getTopic(anyString())).thenReturn(topic);
            when(topic.addListener(eq(String.class), any())).thenReturn(LISTENER_ID);
        }
        broadcaster.init();
        return broadcaster;
    }

    @Test
    @DisplayName("Redisson 不可用：广播发布静默跳过，本地消费仍可用")
    void shouldDegradeWhenRedissonUnavailable() {
        JobScheduleBroadcaster broadcaster = newBroadcaster(false);

        broadcaster.broadcastReschedule(JOB_ID);

        verify(redissonClient, never()).getTopic(anyString());
        broadcaster.handleMessage("other|CANCEL|42");
        assertEquals(1, events.size());
        assertEquals(JOB_ID, events.get(0)[0]);
        broadcaster.destroy();
    }

    @Test
    @DisplayName("广播发布：消息体为 实例标识|动作|任务ID")
    void shouldPublishActionMessage() {
        JobScheduleBroadcaster broadcaster = newBroadcaster(true);

        broadcaster.broadcastReschedule(JOB_ID);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(topic).publish(messageCaptor.capture());
        String message = messageCaptor.getValue();
        assertTrue(message.endsWith("|" + JobScheduleBroadcaster.ACTION_RESCHEDULE + "|" + JOB_ID));
        broadcaster.destroy();
    }

    @Test
    @DisplayName("接收消息：跳过自身回声，其他实例按动作应用")
    @SuppressWarnings("unchecked")
    void shouldApplyRemoteMessageAndSkipSelfEcho() {
        JobScheduleBroadcaster broadcaster = newBroadcaster(true);
        broadcaster.broadcastCancel(JOB_ID);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(topic).publish(messageCaptor.capture());
        String selfMessage = messageCaptor.getValue();

        ArgumentCaptor<MessageListener> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
        verify(topic).addListener(eq(String.class), listenerCaptor.capture());
        MessageListener<String> listener = listenerCaptor.getValue();
        assertNotNull(listener);

        listener.onMessage(JobScheduleBroadcaster.TOPIC_NAME, selfMessage);
        assertTrue(events.isEmpty());

        listener.onMessage(JobScheduleBroadcaster.TOPIC_NAME, "otherInstance|RESCHEDULE|" + JOB_ID);
        assertEquals(1, events.size());
        assertEquals(JOB_ID, events.get(0)[0]);
        assertEquals(Boolean.TRUE, events.get(0)[1]);

        listener.onMessage(JobScheduleBroadcaster.TOPIC_NAME, "otherInstance|CANCEL|" + JOB_ID);
        assertEquals(2, events.size());
        assertFalse((Boolean) events.get(1)[1]);
        broadcaster.destroy();
    }

    @Test
    @DisplayName("接收消息：格式非法、任务ID非法与消费者未就绪均安全忽略")
    void shouldIgnoreMalformedMessage() {
        JobScheduleBroadcaster broadcaster = newBroadcaster(true);

        broadcaster.handleMessage(null);
        broadcaster.handleMessage("bad-message");
        broadcaster.handleMessage("a|b|c|d");
        broadcaster.handleMessage("other|RESCHEDULE|not-a-number");

        assertTrue(events.isEmpty());
        broadcaster.destroy();
    }

    @Test
    @DisplayName("关闭：注销主题监听")
    void shouldRemoveListenerOnDestroy() {
        JobScheduleBroadcaster broadcaster = newBroadcaster(true);

        broadcaster.destroy();

        verify(topic).removeListener(LISTENER_ID);
    }
}
