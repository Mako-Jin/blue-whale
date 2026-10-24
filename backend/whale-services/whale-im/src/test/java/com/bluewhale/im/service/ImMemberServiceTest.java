package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImConversationMember;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import com.bluewhale.im.vo.MemberVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ImMemberService} 单元测试：列表排序、增删权限、退群限制。
 *
 * @author BlueWhale
 */
class ImMemberServiceTest {

    private static final long OPERATOR_ID = 10L;

    private static final long MEMBER_ID = 11L;

    private static final long CONVERSATION_ID = 100L;

    /**
     * 纯 Mockito 单测需手动初始化表信息缓存，否则 Lambda 条件解析列名失败。
     */
    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ImConversationMember.class);
    }

    private final ImConversationMemberMapper memberMapper = mock(ImConversationMemberMapper.class);

    private final ImConversationGuard guard = mock(ImConversationGuard.class);

    private final ImMemberService service = new ImMemberService(memberMapper, guard);

    @Test
    @DisplayName("list：群主优先，其余按角色排序，用户ID以字符串输出")
    void listShouldSortByRole() {
        when(guard.requireMember(CONVERSATION_ID, OPERATOR_ID)).thenReturn(group());
        when(memberMapper.selectList(any())).thenReturn(List.of(
                member(3L, "MEMBER"),
                member(1L, "ADMIN"),
                member(2L, "OWNER")));

        List<MemberVO> result = service.list(CONVERSATION_ID, OPERATOR_ID);

        assertEquals(3, result.size());
        assertEquals("2", result.get(0).getUserId());
        assertEquals("OWNER", result.get(0).getRole());
        assertEquals("1", result.get(1).getUserId());
        assertEquals("3", result.get(2).getUserId());
    }

    @Test
    @DisplayName("add：普通成员无权添加")
    void addShouldRequireManager() {
        when(guard.requireMember(CONVERSATION_ID, OPERATOR_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, OPERATOR_ID)).thenReturn(member(OPERATOR_ID, "MEMBER"));

        assertThrows(BizException.class, () -> service.add(CONVERSATION_ID, OPERATOR_ID, List.of(MEMBER_ID)));
        verify(memberMapper, never()).insert(any(ImConversationMember.class));
    }

    @Test
    @DisplayName("add：已存在成员跳过，仅插入新成员")
    void addShouldSkipExistingMembers() {
        when(guard.requireMember(CONVERSATION_ID, OPERATOR_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, OPERATOR_ID)).thenReturn(member(OPERATOR_ID, "ADMIN"));
        when(memberMapper.selectList(any())).thenReturn(List.of(member(MEMBER_ID, "MEMBER")));

        List<MemberVO> added = service.add(CONVERSATION_ID, OPERATOR_ID, List.of(MEMBER_ID, 12L, 12L));

        assertEquals(1, added.size());
        assertEquals("12", added.get(0).getUserId());
        ArgumentCaptor<ImConversationMember> captor = ArgumentCaptor.forClass(ImConversationMember.class);
        verify(memberMapper, times(1)).insert(captor.capture());
        assertEquals("MEMBER", captor.getValue().getRole());
        assertEquals(0L, captor.getValue().getLastReadSeq());
    }

    @Test
    @DisplayName("add：超出群成员上限时整体拒绝")
    void addShouldRejectWhenOverLimit() {
        when(guard.requireMember(CONVERSATION_ID, OPERATOR_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, OPERATOR_ID)).thenReturn(member(OPERATOR_ID, "OWNER"));
        ImConversationMember[] existing = new ImConversationMember[200];
        for (int i = 0; i < existing.length; i++) {
            existing[i] = member(1000L + i, "MEMBER");
        }
        when(memberMapper.selectList(any())).thenReturn(List.of(existing));

        assertThrows(BizException.class, () -> service.add(CONVERSATION_ID, OPERATOR_ID, List.of(MEMBER_ID)));
    }

    @Test
    @DisplayName("add：单聊不支持成员管理")
    void addShouldRejectSingleConversation() {
        ImConversation single = new ImConversation();
        single.setId(CONVERSATION_ID);
        single.setType("SINGLE");
        when(guard.requireMember(CONVERSATION_ID, OPERATOR_ID)).thenReturn(single);

        assertThrows(BizException.class, () -> service.add(CONVERSATION_ID, OPERATOR_ID, List.of(MEMBER_ID)));
    }

    @Test
    @DisplayName("remove：不能移除群主")
    void removeShouldRejectOwnerTarget() {
        when(guard.requireMember(CONVERSATION_ID, OPERATOR_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, OPERATOR_ID)).thenReturn(member(OPERATOR_ID, "OWNER"));
        when(guard.requireMemberRecord(CONVERSATION_ID, MEMBER_ID)).thenReturn(member(MEMBER_ID, "OWNER"));

        assertThrows(BizException.class, () -> service.remove(CONVERSATION_ID, OPERATOR_ID, MEMBER_ID));
        verify(memberMapper, never()).deleteMember(any(), any());
    }

    @Test
    @DisplayName("remove：管理员不能移除管理员")
    void removeShouldRejectAdminRemovingAdmin() {
        when(guard.requireMember(CONVERSATION_ID, OPERATOR_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, OPERATOR_ID)).thenReturn(member(OPERATOR_ID, "ADMIN"));
        when(guard.requireMemberRecord(CONVERSATION_ID, MEMBER_ID)).thenReturn(member(MEMBER_ID, "ADMIN"));

        assertThrows(BizException.class, () -> service.remove(CONVERSATION_ID, OPERATOR_ID, MEMBER_ID));
    }

    @Test
    @DisplayName("remove：群主移除普通成员成功")
    void removeShouldDeleteMember() {
        when(guard.requireMember(CONVERSATION_ID, OPERATOR_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, OPERATOR_ID)).thenReturn(member(OPERATOR_ID, "OWNER"));
        when(guard.requireMemberRecord(CONVERSATION_ID, MEMBER_ID)).thenReturn(member(MEMBER_ID, "MEMBER"));

        service.remove(CONVERSATION_ID, OPERATOR_ID, MEMBER_ID);

        verify(memberMapper).deleteMember(CONVERSATION_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("quit：群主不能退群")
    void quitShouldRejectOwner() {
        when(guard.requireMember(CONVERSATION_ID, OPERATOR_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, OPERATOR_ID)).thenReturn(member(OPERATOR_ID, "OWNER"));

        assertThrows(BizException.class, () -> service.quit(CONVERSATION_ID, OPERATOR_ID));
        verify(memberMapper, never()).deleteMember(any(), any());
    }

    @Test
    @DisplayName("quit：普通成员退群成功")
    void quitShouldDeleteSelf() {
        when(guard.requireMember(CONVERSATION_ID, OPERATOR_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, OPERATOR_ID)).thenReturn(member(OPERATOR_ID, "MEMBER"));

        service.quit(CONVERSATION_ID, OPERATOR_ID);

        verify(memberMapper).deleteMember(CONVERSATION_ID, OPERATOR_ID);
    }

    @Test
    @DisplayName("add：空成员列表直接返回空集")
    void addShouldReturnEmptyForBlankInput() {
        when(guard.requireMember(CONVERSATION_ID, OPERATOR_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, OPERATOR_ID)).thenReturn(member(OPERATOR_ID, "OWNER"));

        assertTrue(service.add(CONVERSATION_ID, OPERATOR_ID, List.of()).isEmpty());
    }

    /**
     * 构造群聊会话。
     *
     * @return 会话实体
     */
    private static ImConversation group() {
        ImConversation conversation = new ImConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setType("GROUP");
        return conversation;
    }

    /**
     * 构造成员。
     *
     * @param userId 用户ID
     * @param role   角色
     * @return 成员实体
     */
    private static ImConversationMember member(long userId, String role) {
        ImConversationMember member = new ImConversationMember();
        member.setConversationId(CONVERSATION_ID);
        member.setUserId(userId);
        member.setMemberType("USER");
        member.setRole(role);
        member.setLastReadSeq(0L);
        return member;
    }
}
