package com.bluewhale.im.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ImConversationGuard} 单元测试：会话缺失与非成员统一拒绝。
 *
 * @author BlueWhale
 */
class ImConversationGuardTest {

    private static final long USER_ID = 10L;

    private static final long CONVERSATION_ID = 100L;

    private final ImConversationMapper conversationMapper = mock(ImConversationMapper.class);

    private final ImConversationMemberMapper memberMapper = mock(ImConversationMemberMapper.class);

    private final ImConversationGuard guard = new ImConversationGuard(conversationMapper, memberMapper);

    @Test
    @DisplayName("requireMember：会话不存在时拒绝")
    void shouldRejectMissingConversation() {
        when(conversationMapper.selectById(anyLong())).thenReturn(null);

        assertThrows(BizException.class, () -> guard.requireMember(CONVERSATION_ID, USER_ID));
    }

    @Test
    @DisplayName("requireMember：非会话成员时拒绝")
    void shouldRejectNonMember() {
        ImConversation conversation = new ImConversation();
        conversation.setId(CONVERSATION_ID);
        when(conversationMapper.selectById(anyLong())).thenReturn(conversation);
        when(memberMapper.selectCount(any())).thenReturn(0L);

        assertThrows(BizException.class, () -> guard.requireMember(CONVERSATION_ID, USER_ID));
    }

    @Test
    @DisplayName("requireMember：成员校验通过时返回会话")
    void shouldReturnConversationForMember() {
        ImConversation conversation = new ImConversation();
        conversation.setId(CONVERSATION_ID);
        when(conversationMapper.selectById(anyLong())).thenReturn(conversation);
        when(memberMapper.selectCount(any())).thenReturn(1L);

        assertSame(conversation, guard.requireMember(CONVERSATION_ID, USER_ID));
    }
}
