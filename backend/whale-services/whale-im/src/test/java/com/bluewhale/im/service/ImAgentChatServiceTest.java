package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.im.entity.ImAgentBinding;
import com.bluewhale.im.entity.ImAgentRun;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImMessage;
import com.bluewhale.im.mapper.ImAgentBindingMapper;
import com.bluewhale.im.mapper.ImAgentRunMapper;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImMessageMapper;
import com.bluewhale.im.support.AgentStreamClient;
import com.bluewhale.im.ws.ImMessageBroadcaster;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ImAgentChatService} 单元测试：SSE 事件转 WS 帧、回复落库、失败降级。
 *
 * @author BlueWhale
 */
class ImAgentChatServiceTest {

    private static final long CONVERSATION_ID = 100L;

    private static final long USER_ID = 10L;

    private static final long BOT_USER_ID = 900000000000000001L;

    private static final long REPLY_ID = 700L;

    /**
     * 纯 Mockito 单测需手动初始化表信息缓存，否则 Lambda 条件解析列名失败。
     */
    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ImAgentBinding.class);
        TableInfoHelper.initTableInfo(assistant, ImAgentRun.class);
        TableInfoHelper.initTableInfo(assistant, ImConversation.class);
        TableInfoHelper.initTableInfo(assistant, ImMessage.class);
    }

    private final ImAgentBindingMapper bindingMapper = mock(ImAgentBindingMapper.class);

    private final ImAgentRunMapper runMapper = mock(ImAgentRunMapper.class);

    private final ImConversationMapper conversationMapper = mock(ImConversationMapper.class);

    private final ImMessageMapper messageMapper = mock(ImMessageMapper.class);

    private final MessageSeqAllocator seqAllocator = mock(MessageSeqAllocator.class);

    private final ImMessageBroadcaster broadcaster = mock(ImMessageBroadcaster.class);

    private final AgentStreamClient client = mock(AgentStreamClient.class);

    private final ImBotService botService = mock(ImBotService.class);

    private final ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final ImAgentChatService service = new ImAgentChatService(bindingMapper, runMapper,
            conversationMapper, messageMapper, seqAllocator, broadcaster, client, botService,
            objectMapper, executor);

    @Test
    @DisplayName("成功链路：meta/delta/done 转帧，回复以 BOT 落库并推进序号")
    void shouldStreamAndPersistReply() throws Exception {
        stubRunnableExecutor();
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(botConversation());
        when(bindingMapper.selectOne(any())).thenReturn(binding());
        when(botService.botUserId()).thenReturn(BOT_USER_ID);
        when(seqAllocator.next(eq(CONVERSATION_ID), anyLong())).thenReturn(6L);
        when(runMapper.insert(any(ImAgentRun.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ImAgentRun.class).setId(900L);
            return 1;
        });
        when(messageMapper.insert(any(ImMessage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ImMessage.class).setId(REPLY_ID);
            return 1;
        });
        doAnswer(invocation -> {
            AgentStreamClient.EventHandler handler = invocation.getArgument(3);
            handler.onEvent("meta", "{\"conversationId\":\"300\",\"intent\":\"ARCH\",\"degraded\":false}");
            handler.onEvent("delta", "{\"text\":\"你好\"}");
            handler.onEvent("tool", "{\"name\":\"search\",\"result\":\"ok\"}");
            handler.onEvent("done", "{\"tokens\":5,\"degraded\":false,\"citations\":[{\"title\":\"doc\"}]}");
            return null;
        }).when(client).stream(anyString(), eq(USER_ID), any(), any());

        service.onUserMessage(userMessage(), "sys:im:chat");

        verify(broadcaster).broadcastFrame(eq(CONVERSATION_ID),
                argThat(frame -> "agent.meta".equals(frame.get("type"))));
        verify(broadcaster).broadcastFrame(eq(CONVERSATION_ID),
                argThat(frame -> "agent.delta".equals(frame.get("type")) && "你好".equals(frame.get("text"))));
        verify(broadcaster).broadcastFrame(eq(CONVERSATION_ID),
                argThat(frame -> "agent.done".equals(frame.get("type"))
                        && String.valueOf(REPLY_ID).equals(frame.get("messageId"))));

        ArgumentCaptor<ImMessage> messageCaptor = ArgumentCaptor.forClass(ImMessage.class);
        verify(messageMapper).insert(messageCaptor.capture());
        assertEquals("BOT", messageCaptor.getValue().getSenderType());
        assertEquals(Long.valueOf(BOT_USER_ID), messageCaptor.getValue().getSenderId());
        assertEquals("你好", messageCaptor.getValue().getContent());
        verify(conversationMapper).update(any(), any());

        verify(runMapper).update(any(), argThat(wrapper -> true));
    }

    @Test
    @DisplayName("失败链路：智能体异常时落系统提示并推送 agent.error")
    void shouldDegradeWhenAgentFails() throws Exception {
        stubRunnableExecutor();
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(botConversation());
        when(bindingMapper.selectOne(any())).thenReturn(binding());
        when(botService.botUserId()).thenReturn(BOT_USER_ID);
        when(seqAllocator.next(eq(CONVERSATION_ID), anyLong())).thenReturn(6L);
        when(runMapper.insert(any(ImAgentRun.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ImAgentRun.class).setId(900L);
            return 1;
        });
        when(messageMapper.insert(any(ImMessage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ImMessage.class).setId(REPLY_ID);
            return 1;
        });
        doAnswer(invocation -> {
            throw new IOException("因权限不足被拒绝");
        }).when(client).stream(anyString(), anyLong(), any(), any());

        service.onUserMessage(userMessage(), "sys:im:chat");

        verify(broadcaster).broadcastFrame(eq(CONVERSATION_ID),
                argThat(frame -> "agent.error".equals(frame.get("type"))));
        ArgumentCaptor<ImMessage> messageCaptor = ArgumentCaptor.forClass(ImMessage.class);
        verify(messageMapper).insert(messageCaptor.capture());
        assertEquals("SYSTEM", messageCaptor.getValue().getSenderType());
        assertEquals("智能体暂时不可用，请稍后重试", messageCaptor.getValue().getContent());
    }

    @Test
    @DisplayName("跳过链路：非 BOT 会话不调用智能体")
    void shouldSkipNonBotConversation() throws Exception {
        ImConversation single = new ImConversation();
        single.setId(CONVERSATION_ID);
        single.setType("SINGLE");
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(single);

        service.onUserMessage(userMessage(), null);

        verify(executor, never()).execute(any(Runnable.class));
        verify(client, never()).stream(anyString(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("跳过链路：无启用绑定不调用智能体")
    void shouldSkipWhenNoBinding() throws Exception {
        stubRunnableExecutor();
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(botConversation());
        when(bindingMapper.selectOne(any())).thenReturn(null);

        service.onUserMessage(userMessage(), null);

        verify(botService, never()).botUserId();
        verify(client, never()).stream(anyString(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("跳过链路：机器人自身消息不重复触发")
    void shouldSkipBotMessages() {
        ImMessage botMessage = userMessage();
        botMessage.setSenderType("BOT");

        service.onUserMessage(botMessage, null);

        verify(executor, never()).execute(any(Runnable.class));
    }

    /**
     * 让线程池在当前线程同步执行，便于断言。
     */
    private void stubRunnableExecutor() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
    }

    /**
     * 构造 BOT 会话。
     *
     * @return 会话实体
     */
    private static ImConversation botConversation() {
        ImConversation conversation = new ImConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setType("BOT");
        conversation.setOwnerId(USER_ID);
        conversation.setLastSeq(5L);
        return conversation;
    }

    /**
     * 构造机器人绑定。
     *
     * @return 绑定实体
     */
    private static ImAgentBinding binding() {
        ImAgentBinding binding = new ImAgentBinding();
        binding.setId(50L);
        binding.setConversationId(CONVERSATION_ID);
        binding.setBotUserId(BOT_USER_ID);
        binding.setEnabled(1);
        return binding;
    }

    /**
     * 构造用户消息。
     *
     * @return 消息实体
     */
    private static ImMessage userMessage() {
        ImMessage message = new ImMessage();
        message.setId(400L);
        message.setConversationId(CONVERSATION_ID);
        message.setSenderId(USER_ID);
        message.setSenderType("USER");
        message.setContentType("TEXT");
        message.setContent("平台架构如何？");
        message.setStatus(1);
        return message;
    }
}
