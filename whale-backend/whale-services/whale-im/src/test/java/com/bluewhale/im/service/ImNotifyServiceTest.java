package com.bluewhale.im.service;

import com.bluewhale.im.dto.InternalNotifyRequest;
import com.bluewhale.im.vo.ConversationVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ImNotifyService} 单元测试：逐人投递、去重、失败隔离与正文收敛。
 *
 * @author BlueWhale
 */
class ImNotifyServiceTest {

    private static final long BOT_USER_ID = 900L;

    private final ImBotService botService = mock(ImBotService.class);

    private final ImMessageService messageService = mock(ImMessageService.class);

    private final ImNotifyService service = new ImNotifyService(botService, messageService);

    /**
     * 构造机器人会话视图。
     *
     * @param conversationId 会话ID
     * @return 会话视图
     */
    private static ConversationVO conversation(long conversationId) {
        return ConversationVO.builder().id(String.valueOf(conversationId)).build();
    }

    /**
     * 构造通知请求。
     *
     * @param userIds 接收人
     * @param content 正文
     * @return 通知请求
     */
    private static InternalNotifyRequest request(List<Long> userIds, String content) {
        InternalNotifyRequest request = new InternalNotifyRequest();
        request.setUserIds(userIds);
        request.setTitle("工作项提醒");
        request.setContent(content);
        request.setBizType("PM");
        request.setBizId("101");
        request.setClientMsgId("pm-ASSIGN-101-1");
        return request;
    }

    @Test
    @DisplayName("逐人投递：每个接收人各发一条，正文为「标题+换行+正文」")
    void notifyEachRecipient() {
        when(botService.botUserId()).thenReturn(BOT_USER_ID);
        when(botService.openDefault(1L)).thenReturn(conversation(11L));
        when(botService.openDefault(2L)).thenReturn(conversation(22L));

        int delivered = service.notify(request(List.of(1L, 2L), "你被指派了 PM-1"));

        assertEquals(2, delivered);
        verify(messageService).sendAsBot(eq(11L), eq(BOT_USER_ID),
                eq("工作项提醒\n你被指派了 PM-1"), eq("pm-ASSIGN-101-1"));
        verify(messageService).sendAsBot(eq(22L), eq(BOT_USER_ID),
                eq("工作项提醒\n你被指派了 PM-1"), eq("pm-ASSIGN-101-1"));
    }

    @Test
    @DisplayName("去重：重复用户ID只投递一次")
    void deduplicateRecipients() {
        when(botService.botUserId()).thenReturn(BOT_USER_ID);
        when(botService.openDefault(1L)).thenReturn(conversation(11L));

        int delivered = service.notify(request(List.of(1L, 1L, 1L), "正文"));

        assertEquals(1, delivered);
        verify(botService, times(1)).openDefault(1L);
    }

    @Test
    @DisplayName("失败隔离：单个接收人异常时其余接收人仍投递")
    void isolateFailure() {
        when(botService.botUserId()).thenReturn(BOT_USER_ID);
        when(botService.openDefault(1L)).thenThrow(new IllegalStateException("会话初始化失败"));
        when(botService.openDefault(2L)).thenReturn(conversation(22L));

        int delivered = service.notify(request(List.of(1L, 2L), "正文"));

        assertEquals(1, delivered);
        verify(messageService, times(1)).sendAsBot(eq(22L), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("空接收人：不产生任何投递")
    void emptyRecipients() {
        int delivered = service.notify(request(List.of(), "正文"));

        assertEquals(0, delivered);
        verify(botService, never()).openDefault(anyLong());
        verify(messageService, never()).sendAsBot(anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("幂等键缺省：服务端生成 un-notify- 前缀键")
    void generateClientMsgId() {
        when(botService.botUserId()).thenReturn(BOT_USER_ID);
        when(botService.openDefault(1L)).thenReturn(conversation(11L));
        InternalNotifyRequest request = request(List.of(1L), null);
        request.setClientMsgId(null);

        service.notify(request);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messageService).sendAsBot(eq(11L), eq(BOT_USER_ID), eq("工作项提醒"), captor.capture());
        assertTrue(captor.getValue().startsWith("un-notify-"));
    }

    @Test
    @DisplayName("正文收敛：超过 1000 字符截断")
    void truncateContent() {
        when(botService.botUserId()).thenReturn(BOT_USER_ID);
        when(botService.openDefault(1L)).thenReturn(conversation(11L));
        String longContent = "x".repeat(1500);

        service.notify(request(List.of(1L), longContent));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messageService).sendAsBot(eq(11L), eq(BOT_USER_ID), captor.capture(), anyString());
        assertEquals(1000, captor.getValue().length());
    }
}
