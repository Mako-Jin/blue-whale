package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.im.entity.ImAgentRun;
import com.bluewhale.im.entity.ImMessage;
import com.bluewhale.im.mapper.ImAgentRunMapper;
import com.bluewhale.im.support.AgentStreamClient;
import com.bluewhale.im.ws.ImMessageBroadcaster;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ImAgentRunService} 单元测试：确认执行、拒绝与状态校验。
 *
 * @author BlueWhale
 */
class ImAgentRunServiceTest {

    private static final long RUN_ID = 900L;

    private static final long CONVERSATION_ID = 100L;

    private static final long USER_ID = 10L;

    private static final long BOT_USER_ID = 900000000000000001L;

    private static final long MESSAGE_ID = 700L;

    /**
     * 纯 Mockito 单测需手动初始化表信息缓存，否则 Lambda 条件解析列名失败。
     */
    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ImAgentRun.class);
    }

    private final ImAgentRunMapper runMapper = mock(ImAgentRunMapper.class);

    private final ImConversationGuard guard = mock(ImConversationGuard.class);

    private final AgentStreamClient client = mock(AgentStreamClient.class);

    private final ImAgentChatService chatService = mock(ImAgentChatService.class);

    private final ImBotService botService = mock(ImBotService.class);

    private final ImMessageBroadcaster broadcaster = mock(ImMessageBroadcaster.class);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final ImAgentRunService service = new ImAgentRunService(runMapper, guard, client,
            chatService, botService, broadcaster, objectMapper);

    @BeforeEach
    void setUp() {
        when(botService.botUserId()).thenReturn(BOT_USER_ID);
        when(chatService.appendMessage(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    ImMessage message = new ImMessage();
                    message.setId(MESSAGE_ID);
                    message.setConversationId(CONVERSATION_ID);
                    message.setContent(invocation.getArgument(4));
                    return message;
                });
    }

    @Test
    @DisplayName("确认执行：回写 SUCCESS 并广播结果消息")
    void shouldConfirmAndComplete() throws Exception {
        when(runMapper.selectById(RUN_ID)).thenReturn(waitingRun());
        when(runMapper.update(any(), any())).thenReturn(1);
        JsonNode data = objectMapper.readTree("{\"id\":\"12\"}");
        when(client.confirmOperation("c-1", true, USER_ID, "sys:ai:ops:form")).thenReturn(data);

        Map<String, Object> result = service.confirm(RUN_ID, true, USER_ID, "sys:ai:ops:form");

        assertThat(result).containsEntry("status", "SUCCESS").containsEntry("messageId", String.valueOf(MESSAGE_ID));
        verify(guard).requireMember(CONVERSATION_ID, USER_ID);
        verify(broadcaster).broadcast(eq(CONVERSATION_ID), any());
    }

    @Test
    @DisplayName("拒绝：代理 reject 并置 FAILED")
    void shouldRejectAndMarkFailed() throws Exception {
        when(runMapper.selectById(RUN_ID)).thenReturn(waitingRun());
        when(runMapper.update(any(), any())).thenReturn(1);
        when(client.confirmOperation("c-1", false, USER_ID, null)).thenReturn(objectMapper.readTree("{}"));

        Map<String, Object> result = service.confirm(RUN_ID, false, USER_ID, null);

        assertThat(result).containsEntry("status", "FAILED");
    }

    @Test
    @DisplayName("执行失败：置 FAILED 且不抛出")
    void shouldMarkFailedWhenAgentFails() throws Exception {
        when(runMapper.selectById(RUN_ID)).thenReturn(waitingRun());
        when(runMapper.update(any(), any())).thenReturn(1);
        when(client.confirmOperation(anyString(), eq(true), anyLong(), any()))
                .thenThrow(new IOException("确认令牌已使用或已失效"));

        Map<String, Object> result = service.confirm(RUN_ID, true, USER_ID, "sys:ai:ops:form");

        assertThat(result).containsEntry("status", "FAILED");
    }

    @Test
    @DisplayName("状态校验：不在待确认状态或非成员时拒绝")
    void shouldRejectWhenNotWaitingConfirmOrNotMember() throws Exception {
        ImAgentRun running = waitingRun();
        running.setStatus("RUNNING");
        when(runMapper.selectById(RUN_ID)).thenReturn(running);

        assertThatThrownBy(() -> service.confirm(RUN_ID, true, USER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不在待确认状态");

        when(runMapper.selectById(RUN_ID)).thenReturn(waitingRun());
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenThrow(new BizException("会话不存在或无权访问"));

        assertThatThrownBy(() -> service.confirm(RUN_ID, true, USER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权访问");
        verify(client, never()).confirmOperation(anyString(), eq(true), anyLong(), any());
    }

    /**
     * 构造待确认运行记录。
     *
     * @return 运行记录
     */
    private static ImAgentRun waitingRun() {
        ImAgentRun run = new ImAgentRun();
        run.setId(RUN_ID);
        run.setConversationId(CONVERSATION_ID);
        run.setStatus("WAITING_CONFIRM");
        run.setConfirmationJson("{\"confirmationId\":\"c-1\",\"operation\":\"form.definition.create\","
                + "\"summary\":\"创建表单定义：示例（demo）\",\"permission\":\"sys:ai:ops:form\"}");
        return run;
    }
}
