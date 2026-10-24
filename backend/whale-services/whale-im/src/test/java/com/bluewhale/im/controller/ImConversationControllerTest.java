package com.bluewhale.im.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImMessage;
import com.bluewhale.im.service.ImConversationService;
import com.bluewhale.im.service.ImMemberService;
import com.bluewhale.im.service.ImMessageService;
import com.bluewhale.im.vo.ConversationVO;
import com.bluewhale.im.vo.MemberVO;
import com.bluewhale.im.vo.MessagePageVO;
import com.bluewhale.im.vo.MessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ImConversationController} 控制器层单元测试（standalone MockMvc）。
 *
 * <p>覆盖权限注解、会话列表、创建校验、消息发送与已读上报。</p>
 *
 * @author BlueWhale
 */
class ImConversationControllerTest {

    private static final long USER_ID = 10L;

    private static final long CONVERSATION_ID = 100L;

    private final ImConversationService conversationService = mock(ImConversationService.class);

    private final ImMessageService messageService = mock(ImMessageService.class);

    private final ImMemberService memberService = mock(ImMemberService.class);

    private final ImConversationController controller =
            new ImConversationController(conversationService, messageService, memberService);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：注册控制器与校验器，不装配权限拦截器
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("类级权限：全类要求 sys:im:chat")
    void classShouldRequireImChatPermission() {
        RequirePermission annotation = ImConversationController.class.getAnnotation(RequirePermission.class);

        assertNotNull(annotation);
        assertEquals("sys:im:chat", annotation.value());
    }

    @Test
    @DisplayName("GET /im/conversations：返回当前用户会话列表")
    void listShouldReturnConversations() throws Exception {
        ConversationVO vo = ConversationVO.builder().id("100").type("SINGLE").unread(2L).build();
        when(conversationService.list(USER_ID)).thenReturn(List.of(vo));

        mockMvc.perform(get("/im/conversations").header(WebHeaders.USER_ID, String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value("100"))
                .andExpect(jsonPath("$.data[0].unread").value(2));
    }

    @Test
    @DisplayName("POST /im/conversations：类型非法时返回 400")
    void createShouldRejectInvalidType() throws Exception {
        mockMvc.perform(post("/im/conversations")
                        .header(WebHeaders.USER_ID, String.valueOf(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PUBLIC\",\"memberIds\":[2]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /im/conversations：合法单聊返回会话ID")
    void createShouldReturnConversation() throws Exception {
        ImConversation conversation = new ImConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setType("SINGLE");
        conversation.setOwnerId(USER_ID);
        conversation.setLastSeq(0L);
        when(conversationService.create(anyLong(), any())).thenReturn(conversation);

        mockMvc.perform(post("/im/conversations")
                        .header(WebHeaders.USER_ID, String.valueOf(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SINGLE\",\"memberIds\":[2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("100"))
                .andExpect(jsonPath("$.data.type").value("SINGLE"));
    }

    @Test
    @DisplayName("GET /im/conversations/{id}/messages：返回游标页")
    void messagesShouldReturnPage() throws Exception {
        MessageVO vo = MessageVO.builder().id("1").seq(5L).content("hi").build();
        MessagePageVO page = MessagePageVO.builder().records(List.of(vo)).hasMore(false).build();
        when(messageService.history(CONVERSATION_ID, USER_ID, null, null, 30)).thenReturn(page);

        mockMvc.perform(get("/im/conversations/{id}/messages", CONVERSATION_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].seq").value(5))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    @DisplayName("POST /im/conversations/{id}/messages：返回落库消息")
    void sendShouldReturnMessage() throws Exception {
        ImMessage message = new ImMessage();
        message.setId(1L);
        message.setConversationId(CONVERSATION_ID);
        message.setSeq(5L);
        message.setSenderId(USER_ID);
        message.setSenderType("USER");
        message.setContentType("TEXT");
        message.setContent("hi");
        message.setClientMsgId("c-1");
        message.setStatus(1);
        when(messageService.send(anyLong(), anyLong(), any())).thenReturn(message);

        mockMvc.perform(post("/im/conversations/{id}/messages", CONVERSATION_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMsgId\":\"c-1\",\"contentType\":\"TEXT\",\"content\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seq").value(5))
                .andExpect(jsonPath("$.data.clientMsgId").value("c-1"));
    }

    @Test
    @DisplayName("POST /im/conversations/{id}/read：透传已读序号")
    void readShouldDelegateToService() throws Exception {
        mockMvc.perform(post("/im/conversations/{id}/read", CONVERSATION_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seq\":5}"))
                .andExpect(status().isOk());

        verify(conversationService).markRead(CONVERSATION_ID, USER_ID, 5L);
    }

    @Test
    @DisplayName("POST /im/conversations/{id}/messages/{messageId}/recall：透传撤回")
    void recallShouldDelegateToService() throws Exception {
        mockMvc.perform(post("/im/conversations/{id}/messages/{messageId}/recall", CONVERSATION_ID, 9L)
                        .header(WebHeaders.USER_ID, String.valueOf(USER_ID)))
                .andExpect(status().isOk());

        verify(messageService).recall(CONVERSATION_ID, USER_ID, 9L);
    }

    @Test
    @DisplayName("GET /im/conversations/{id}/members：返回成员列表")
    void membersShouldReturnList() throws Exception {
        when(memberService.list(CONVERSATION_ID, USER_ID))
                .thenReturn(List.of(MemberVO.builder().userId("10").role("OWNER").build()));

        mockMvc.perform(get("/im/conversations/{id}/members", CONVERSATION_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value("10"))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"));
    }

    @Test
    @DisplayName("POST /im/conversations/{id}/members：memberIds 为空时返回 400")
    void addMembersShouldRejectEmpty() throws Exception {
        mockMvc.perform(post("/im/conversations/{id}/members", CONVERSATION_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /im/conversations/{id}/members/{userId}：透传移除成员")
    void removeMemberShouldDelegateToService() throws Exception {
        mockMvc.perform(delete("/im/conversations/{id}/members/{userId}", CONVERSATION_ID, 9L)
                        .header(WebHeaders.USER_ID, String.valueOf(USER_ID)))
                .andExpect(status().isOk());

        verify(memberService).remove(CONVERSATION_ID, USER_ID, 9L);
    }

    @Test
    @DisplayName("POST /im/conversations/{id}/quit：透传退群")
    void quitShouldDelegateToService() throws Exception {
        mockMvc.perform(post("/im/conversations/{id}/quit", CONVERSATION_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(USER_ID)))
                .andExpect(status().isOk());

        verify(memberService).quit(CONVERSATION_ID, USER_ID);
    }
}
