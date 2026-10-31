package com.bluewhale.im.service;

import com.bluewhale.im.dto.SendMessageRequest;
import com.bluewhale.im.entity.ImMessage;
import com.bluewhale.im.vo.MessageVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发送消息应用服务单测：落库、机器人钩子与视图装配（T-18 下沉）。
 *
 * @author BlueWhale
 */
class ImMessageSendServiceTest {

    private static final long CONVERSATION_ID = 100L;

    private static final long USER_ID = 10L;

    private final ImMessageService messageService = mock(ImMessageService.class);

    private final ImAgentChatService agentChatService = mock(ImAgentChatService.class);

    private final ImMessageSendService sendService = new ImMessageSendService(messageService, agentChatService);

    @Test
    @DisplayName("发送成功：触发机器人并返回装配后的消息视图")
    void shouldSendAndTriggerAgent() {
        ImMessage message = new ImMessage();
        message.setId(1L);
        message.setConversationId(CONVERSATION_ID);
        message.setSeq(5L);
        message.setSenderId(USER_ID);
        message.setSenderType(ImMessageService.SENDER_USER);
        message.setContentType("TEXT");
        message.setContent("hi");
        message.setClientMsgId("c-1");
        message.setStatus(1);
        when(messageService.send(anyLong(), anyLong(), any())).thenReturn(message);

        MessageVO vo = sendService.send(CONVERSATION_ID, USER_ID, mock(SendMessageRequest.class), "sys:im:chat");

        assertThat(vo.getId()).isEqualTo("1");
        assertThat(vo.getSeq()).isEqualTo(5L);
        assertThat(vo.getClientMsgId()).isEqualTo("c-1");
        verify(agentChatService).onUserMessage(message, "sys:im:chat");
    }
}
