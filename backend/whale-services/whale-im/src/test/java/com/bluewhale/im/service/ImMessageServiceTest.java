package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.im.dto.SendMessageRequest;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImConversationMember;
import com.bluewhale.im.entity.ImMessage;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import com.bluewhale.im.mapper.ImMessageMapper;
import com.bluewhale.im.vo.MessagePageVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ImMessageService} 单元测试：clientMsgId 幂等、序号分配与冲突重试、游标拉取方向。
 *
 * @author BlueWhale
 */
class ImMessageServiceTest {

    private static final long USER_ID = 10L;

    private static final long CONVERSATION_ID = 100L;

    /**
     * 纯 Mockito 单测不经过 MyBatis 启动流程，需手动初始化表信息缓存，
     * 否则 {@code LambdaUpdateWrapper.set(...)} 解析列名时找不到缓存。
     */
    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ImConversation.class);
        TableInfoHelper.initTableInfo(assistant, ImConversationMember.class);
        TableInfoHelper.initTableInfo(assistant, ImMessage.class);
    }

    private final ImMessageMapper messageMapper = mock(ImMessageMapper.class);

    private final ImConversationMapper conversationMapper = mock(ImConversationMapper.class);

    private final ImConversationMemberMapper memberMapper = mock(ImConversationMemberMapper.class);

    private final ImConversationGuard guard = mock(ImConversationGuard.class);

    private final MessageSeqAllocator seqAllocator = mock(MessageSeqAllocator.class);

    private final ImMessageService service = new ImMessageService(
            messageMapper, conversationMapper, memberMapper, guard, seqAllocator);

    @Test
    @DisplayName("send：同一 clientMsgId 重复提交返回首次落库消息，不再插入")
    void sendShouldBeIdempotentByClientMsgId() {
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(conversation(4L));
        ImMessage existing = new ImMessage();
        existing.setId(900L);
        existing.setSeq(3L);
        when(messageMapper.selectOne(any())).thenReturn(existing);

        ImMessage result = service.send(CONVERSATION_ID, USER_ID, request("c-1"));

        assertSame(existing, result);
        verify(messageMapper, never()).insert(any(ImMessage.class));
        verify(conversationMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("send：正常路径分配序号、落库并推进会话与发送者已读游标")
    void sendShouldAllocateSeqAndTouchConversation() {
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(conversation(4L));
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(seqAllocator.next(CONVERSATION_ID, 4L)).thenReturn(5L);
        stubInsertId(messageMapper);

        ImMessage result = service.send(CONVERSATION_ID, USER_ID, request("c-2"));

        assertEquals(5L, result.getSeq());
        assertEquals("USER", result.getSenderType());
        assertEquals(1, result.getStatus());
        assertEquals("c-2", result.getClientMsgId());
        verify(conversationMapper).update(isNull(), any());
        verify(memberMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("send：序号冲突后重新分配并成功落库")
    void sendShouldRetryOnSeqConflict() {
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(conversation(4L));
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(seqAllocator.next(CONVERSATION_ID, 4L)).thenReturn(5L, 6L);
        doThrow(new DuplicateKeyException("dup")).doAnswer(invocation -> {
            ImMessage message = invocation.getArgument(0);
            message.setId(901L);
            return 1;
        }).when(messageMapper).insert(any(ImMessage.class));

        ImMessage result = service.send(CONVERSATION_ID, USER_ID, request("c-3"));

        assertEquals(6L, result.getSeq());
        verify(messageMapper, times(2)).insert(any(ImMessage.class));
    }

    @Test
    @DisplayName("send：连续序号冲突且无幂等命中时抛业务异常")
    void sendShouldFailAfterMaxAttempts() {
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(conversation(4L));
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(seqAllocator.next(anyLong(), anyLong())).thenReturn(5L);
        when(messageMapper.insert(any(ImMessage.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThrows(BizException.class, () -> service.send(CONVERSATION_ID, USER_ID, request("c-4")));
    }

    @Test
    @DisplayName("history：afterSeq 断线补拉按 seq 升序且 hasMore 正确")
    void historyAfterSeqShouldBeAscending() {
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(conversation(9L));
        when(messageMapper.selectList(any())).thenReturn(List.of(message(6L), message(7L), message(8L)));

        MessagePageVO page = service.history(CONVERSATION_ID, USER_ID, null, 5L, 2);

        assertEquals(2, page.getRecords().size());
        assertEquals(6L, page.getRecords().get(0).getSeq());
        assertEquals(7L, page.getRecords().get(1).getSeq());
        assertTrue(page.isHasMore());
    }

    @Test
    @DisplayName("history：默认最近一页倒序取数后反转为升序")
    void historyLatestShouldReverseToAscending() {
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(conversation(9L));
        when(messageMapper.selectList(any())).thenReturn(List.of(message(9L), message(8L), message(7L)));

        MessagePageVO page = service.history(CONVERSATION_ID, USER_ID, null, null, 2);

        assertEquals(2, page.getRecords().size());
        assertEquals(8L, page.getRecords().get(0).getSeq());
        assertEquals(9L, page.getRecords().get(1).getSeq());
        assertTrue(page.isHasMore());
    }

    /**
     * 构造发送请求
     *
     * @param clientMsgId 客户端消息ID
     * @return 发送请求
     */
    private static SendMessageRequest request(String clientMsgId) {
        SendMessageRequest request = new SendMessageRequest();
        request.setClientMsgId(clientMsgId);
        request.setContentType("TEXT");
        request.setContent("你好");
        return request;
    }

    /**
     * 构造会话
     *
     * @param lastSeq 当前最大序号
     * @return 会话实体
     */
    private static ImConversation conversation(long lastSeq) {
        ImConversation conversation = new ImConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setLastSeq(lastSeq);
        return conversation;
    }

    /**
     * 构造消息
     *
     * @param seq 序号
     * @return 消息实体
     */
    private static ImMessage message(long seq) {
        ImMessage message = new ImMessage();
        message.setId(seq);
        message.setConversationId(CONVERSATION_ID);
        message.setSeq(seq);
        message.setSenderId(USER_ID);
        message.setSenderType("USER");
        message.setContentType("TEXT");
        message.setContent("m-" + seq);
        message.setStatus(1);
        return message;
    }

    /**
     * 让 mock 的 insert 回填主键
     *
     * @param mapper 消息 Mapper
     */
    private static void stubInsertId(ImMessageMapper mapper) {
        doAnswer(invocation -> {
            ImMessage message = invocation.getArgument(0);
            message.setId(800L);
            return 1;
        }).when(mapper).insert(any(ImMessage.class));
    }
}
