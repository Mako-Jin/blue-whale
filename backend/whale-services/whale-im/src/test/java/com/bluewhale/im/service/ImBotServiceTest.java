package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.im.entity.ImAgentBinding;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImConversationMember;
import com.bluewhale.im.mapper.ImAgentBindingMapper;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import com.bluewhale.im.vo.ConversationVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ImBotService} 单元测试：BOT 会话首次创建、幂等返回、绑定补齐。
 *
 * @author BlueWhale
 */
class ImBotServiceTest {

    private static final long USER_ID = 10L;

    private static final long BOT_USER_ID = 900000000000000001L;

    private static final long CONVERSATION_ID = 100L;

    /**
     * 纯 Mockito 单测需手动初始化表信息缓存，否则 Lambda 条件解析列名失败。
     */
    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ImConversation.class);
        TableInfoHelper.initTableInfo(assistant, ImConversationMember.class);
        TableInfoHelper.initTableInfo(assistant, ImAgentBinding.class);
    }

    private final ImConversationMapper conversationMapper = mock(ImConversationMapper.class);

    private final ImConversationMemberMapper memberMapper = mock(ImConversationMemberMapper.class);

    private final ImAgentBindingMapper bindingMapper = mock(ImAgentBindingMapper.class);

    private final ImBotService service = new ImBotService(conversationMapper, memberMapper, bindingMapper,
            "蓝鲸助手", BOT_USER_ID, "DEFAULT");

    @Test
    @DisplayName("openDefault：首次调用创建 BOT 会话、双成员与绑定")
    void shouldCreateBotConversationOnFirstCall() {
        when(conversationMapper.selectOne(any())).thenReturn(null);
        when(memberMapper.selectOne(any())).thenReturn(null);

        ConversationVO vo = service.openDefault(USER_ID);

        assertEquals("BOT", vo.getType());
        assertEquals("蓝鲸助手", vo.getTitle());
        assertEquals("10", vo.getOwnerId());
        assertEquals(0L, vo.getUnread());

        ArgumentCaptor<ImConversation> conversationCaptor = ArgumentCaptor.forClass(ImConversation.class);
        verify(conversationMapper).insert(conversationCaptor.capture());
        assertEquals("BOT", conversationCaptor.getValue().getType());

        ArgumentCaptor<ImConversationMember> memberCaptor = ArgumentCaptor.forClass(ImConversationMember.class);
        verify(memberMapper, times(2)).insert(memberCaptor.capture());
        List<ImConversationMember> members = memberCaptor.getAllValues();
        assertEquals(Long.valueOf(USER_ID), members.get(0).getUserId());
        assertEquals("USER", members.get(0).getMemberType());
        assertEquals("OWNER", members.get(0).getRole());
        assertEquals(Long.valueOf(BOT_USER_ID), members.get(1).getUserId());
        assertEquals("BOT", members.get(1).getMemberType());

        ArgumentCaptor<ImAgentBinding> bindingCaptor = ArgumentCaptor.forClass(ImAgentBinding.class);
        verify(bindingMapper).insert(bindingCaptor.capture());
        assertEquals(Long.valueOf(BOT_USER_ID), bindingCaptor.getValue().getBotUserId());
        assertEquals("DEFAULT", bindingCaptor.getValue().getAgentScenario());
        assertEquals(1, bindingCaptor.getValue().getEnabled());
    }

    @Test
    @DisplayName("openDefault：已存在会话时返回既有会话及未读数，不重复创建")
    void shouldReturnExistingConversation() {
        ImConversation conversation = conversation(5L);
        when(conversationMapper.selectOne(any())).thenReturn(conversation);
        ImConversationMember member = new ImConversationMember();
        member.setLastReadSeq(2L);
        when(memberMapper.selectOne(any())).thenReturn(member);
        when(memberMapper.selectCount(any())).thenReturn(1L);
        when(bindingMapper.selectCount(any())).thenReturn(1L);

        ConversationVO vo = service.openDefault(USER_ID);

        assertEquals("100", vo.getId());
        assertEquals(3L, vo.getUnread());
        verify(conversationMapper, never()).insert(any(ImConversation.class));
        verify(memberMapper, never()).insert(any(ImConversationMember.class));
        verify(bindingMapper, never()).insert(any(ImAgentBinding.class));
    }

    @Test
    @DisplayName("openDefault：绑定缺失时自动补齐绑定")
    void shouldRepairMissingBinding() {
        when(conversationMapper.selectOne(any())).thenReturn(conversation(0L));
        when(memberMapper.selectCount(any())).thenReturn(1L);
        when(bindingMapper.selectCount(any())).thenReturn(0L);
        when(memberMapper.selectOne(any())).thenReturn(null);

        service.openDefault(USER_ID);

        verify(bindingMapper).insert(any(ImAgentBinding.class));
        verify(conversationMapper, never()).insert(any(ImConversation.class));
    }

    @Test
    @DisplayName("botUserId：返回配置的机器人逻辑用户ID")
    void shouldExposeBotUserId() {
        assertEquals(Long.valueOf(BOT_USER_ID), service.botUserId());
    }

    @Test
    @DisplayName("openDefault：成员缺失时自动补齐用户成员")
    void shouldRepairMissingMembers() {
        when(conversationMapper.selectOne(any())).thenReturn(conversation(0L));
        when(memberMapper.selectCount(any())).thenReturn(0L);
        when(bindingMapper.selectCount(any())).thenReturn(1L);
        when(memberMapper.selectOne(any())).thenReturn(null);

        service.openDefault(USER_ID);

        verify(memberMapper, times(1)).insert(any(ImConversationMember.class));
    }

    /**
     * 构造既有 BOT 会话。
     *
     * @param lastSeq 最大序号
     * @return 会话实体
     */
    private static ImConversation conversation(long lastSeq) {
        ImConversation conversation = new ImConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setType("BOT");
        conversation.setTitle("蓝鲸助手");
        conversation.setOwnerId(USER_ID);
        conversation.setLastSeq(lastSeq);
        return conversation;
    }
}
