package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
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
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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

    private final ImConversationGuard guard = mock(ImConversationGuard.class);

    private final ImBotService service = new ImBotService(conversationMapper, memberMapper, bindingMapper,
            guard, "蓝鲸助手", BOT_USER_ID, "DEFAULT");

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

    @Test
    @DisplayName("botName：返回配置的机器人展示名")
    void shouldExposeBotName() {
        assertEquals("蓝鲸助手", service.botName());
    }

    @Test
    @DisplayName("ensureGroupBinding：补齐机器人成员与绑定并返回启用绑定")
    void shouldEnsureGroupBinding() {
        ImAgentBinding existing = new ImAgentBinding();
        existing.setId(50L);
        existing.setConversationId(CONVERSATION_ID);
        existing.setBotUserId(BOT_USER_ID);
        existing.setEnabled(1);
        when(memberMapper.selectCount(any())).thenReturn(0L);
        when(bindingMapper.selectCount(any())).thenReturn(0L);
        when(bindingMapper.selectOne(any())).thenReturn(existing);

        ImAgentBinding result = service.ensureGroupBinding(CONVERSATION_ID);

        assertEquals(existing, result);
        ArgumentCaptor<ImConversationMember> memberCaptor = ArgumentCaptor.forClass(ImConversationMember.class);
        verify(memberMapper).insert(memberCaptor.capture());
        assertEquals(Long.valueOf(BOT_USER_ID), memberCaptor.getValue().getUserId());
        assertEquals("BOT", memberCaptor.getValue().getMemberType());
        verify(bindingMapper).insert(any(ImAgentBinding.class));
    }

    @Test
    @DisplayName("ensureGroupBinding：已被停用的绑定不重复启用")
    void shouldKeepDisabledGroupBinding() {
        ImAgentBinding disabled = new ImAgentBinding();
        disabled.setId(50L);
        disabled.setEnabled(0);
        when(memberMapper.selectCount(any())).thenReturn(1L);
        when(bindingMapper.selectCount(any())).thenReturn(1L);
        when(bindingMapper.selectOne(any())).thenReturn(disabled);

        ImAgentBinding result = service.ensureGroupBinding(CONVERSATION_ID);

        assertEquals(0, result.getEnabled());
        verify(bindingMapper, never()).insert(any(ImAgentBinding.class));
    }

    @Test
    @DisplayName("openDefault：并发首建撞唯一索引时返回已落库会话")
    void shouldRecoverFromDuplicateKeyOnConcurrentCreate() {
        when(conversationMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn(conversation(0L));
        when(conversationMapper.insert(any(ImConversation.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        when(memberMapper.selectCount(any())).thenReturn(1L);
        when(bindingMapper.selectCount(any())).thenReturn(1L);

        ConversationVO vo = service.openDefault(USER_ID);

        assertEquals("100", vo.getId());
        verify(conversationMapper, times(1)).insert(any(ImConversation.class));
        verify(bindingMapper, never()).insert(any(ImAgentBinding.class));
    }

    @Test
    @DisplayName("setGroupBotEnabled：管理员可停用群机器人")
    void shouldToggleGroupBotForAdmin() {
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(groupConversation());
        when(guard.requireMemberRecord(CONVERSATION_ID, USER_ID)).thenReturn(operator("ADMIN"));
        ImAgentBinding binding = new ImAgentBinding();
        binding.setId(50L);
        when(bindingMapper.selectOne(any())).thenReturn(binding);

        service.setGroupBotEnabled(CONVERSATION_ID, USER_ID, false);

        verify(bindingMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("setGroupBotEnabled：普通成员无权操作")
    void shouldRejectPlainMemberToggle() {
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(groupConversation());
        when(guard.requireMemberRecord(CONVERSATION_ID, USER_ID)).thenReturn(operator("MEMBER"));

        assertThrows(BizException.class, () -> service.setGroupBotEnabled(CONVERSATION_ID, USER_ID, false));
        verify(bindingMapper, never()).update(isNull(), any());
    }

    /**
     * 构造群聊会话。
     *
     * @return 会话实体
     */
    private static ImConversation groupConversation() {
        ImConversation conversation = new ImConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setType("GROUP");
        conversation.setOwnerId(USER_ID);
        return conversation;
    }

    /**
     * 构造操作人成员记录。
     *
     * @param role 角色
     * @return 成员实体
     */
    private static ImConversationMember operator(String role) {
        ImConversationMember member = new ImConversationMember();
        member.setConversationId(CONVERSATION_ID);
        member.setUserId(USER_ID);
        member.setMemberType("USER");
        member.setRole(role);
        return member;
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
