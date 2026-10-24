package com.bluewhale.im.ws;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.im.entity.ImConversationMember;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import com.bluewhale.im.vo.MessageVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ImMessageBroadcaster} 单元测试：成员解析与推送失败兜底。
 *
 * @author BlueWhale
 */
class ImMessageBroadcasterTest {

    private static final long CONVERSATION_ID = 100L;

    /**
     * 纯 Mockito 单测不经过 MyBatis 启动流程，需手动初始化表信息缓存，
     * 否则 {@code LambdaQueryWrapper.select(...)} 解析列名时找不到缓存。
     */
    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ImConversationMember.class);
    }

    private final ImConversationMemberMapper memberMapper = mock(ImConversationMemberMapper.class);

    private final WsSessionRegistry registry = mock(WsSessionRegistry.class);

    private final ImMessageBroadcaster broadcaster = new ImMessageBroadcaster(memberMapper, registry);

    @Test
    @DisplayName("broadcast：向会话全部成员推送 message.new 帧")
    void broadcastShouldSendToAllMembers() {
        when(memberMapper.selectList(any())).thenReturn(List.of(member(10L), member(11L)));
        MessageVO message = MessageVO.builder().id("1").seq(5L).content("hi").build();

        broadcaster.broadcast(CONVERSATION_ID, message);

        verify(registry).send(eq(List.of(10L, 11L)), any());
    }

    @Test
    @DisplayName("broadcast：成员查询失败时仅记录日志，不影响消息发送")
    void broadcastShouldSwallowFailure() {
        when(memberMapper.selectList(any())).thenThrow(new RuntimeException("db down"));
        MessageVO message = MessageVO.builder().id("1").seq(5L).build();

        assertDoesNotThrow(() -> broadcaster.broadcast(CONVERSATION_ID, message));
    }

    @Test
    @DisplayName("broadcastRecalled：向会话全部成员推送撤回帧")
    void broadcastRecalledShouldSendToAllMembers() {
        when(memberMapper.selectList(any())).thenReturn(List.of(member(10L), member(11L)));

        broadcaster.broadcastRecalled(CONVERSATION_ID, 9L, 5L, 10L);

        verify(registry).send(eq(List.of(10L, 11L)), any());
    }

    /**
     * 构造会话成员
     *
     * @param userId 成员用户ID
     * @return 成员实体
     */
    private static ImConversationMember member(long userId) {
        ImConversationMember member = new ImConversationMember();
        member.setConversationId(CONVERSATION_ID);
        member.setUserId(userId);
        return member;
    }
}
