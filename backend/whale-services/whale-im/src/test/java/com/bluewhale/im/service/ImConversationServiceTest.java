package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.im.dto.CreateConversationRequest;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImConversationMember;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import com.bluewhale.im.support.UserDirectoryClient;
import com.bluewhale.im.vo.ConversationVO;
import com.bluewhale.im.vo.MessageVO;
import com.bluewhale.im.ws.ImMessageBroadcaster;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ImConversationService} 单元测试：创建校验、成员落库、未读数计算、已读游标收敛。
 *
 * @author BlueWhale
 */
class ImConversationServiceTest {

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
    }

    private final ImConversationMapper conversationMapper = mock(ImConversationMapper.class);

    private final ImConversationMemberMapper memberMapper = mock(ImConversationMemberMapper.class);

    private final ImConversationGuard guard = mock(ImConversationGuard.class);

    private final ImMessageService messageService = mock(ImMessageService.class);

    private final ImMessageBroadcaster broadcaster = mock(ImMessageBroadcaster.class);

    private final UserDirectoryClient userDirectoryClient = mock(UserDirectoryClient.class);

    private final ImConversationService service =
            new ImConversationService(conversationMapper, memberMapper, guard, messageService, broadcaster,
                    userDirectoryClient);

    /**
     * 默认放行成员合法性校验，具体用例按需覆盖。
     */
    @BeforeEach
    void setUp() {
        when(userDirectoryClient.missingUserIds(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("create：成员不存在或已停用时拒绝创建")
    void createShouldRejectMissingMembers() {
        when(userDirectoryClient.missingUserIds(any())).thenReturn(List.of(2L));

        assertThrows(BizException.class, () -> service.create(USER_ID, request("SINGLE", null, List.of(2L))));
        verify(conversationMapper, never()).insert(any(ImConversation.class));
    }

    @Test
    @DisplayName("create：单聊必须且只能指定 1 位对方")
    void createSingleShouldRejectWrongPeerCount() {
        CreateConversationRequest request = request("SINGLE", null, List.of(1L, 2L));

        assertThrows(BizException.class, () -> service.create(USER_ID, request));
        verify(conversationMapper, never()).insert(any(ImConversation.class));
    }

    @Test
    @DisplayName("create：单聊不能把自己当对方")
    void createSingleShouldRejectSelfOnly() {
        CreateConversationRequest request = request("SINGLE", null, List.of(USER_ID));

        assertThrows(BizException.class, () -> service.create(USER_ID, request));
    }

    @Test
    @DisplayName("create：群聊必须填写标题")
    void createGroupShouldRequireTitle() {
        CreateConversationRequest request = request("GROUP", "  ", List.of(2L));

        assertThrows(BizException.class, () -> service.create(USER_ID, request));
        verify(conversationMapper, never()).insert(any(ImConversation.class));
    }

    @Test
    @DisplayName("create：单聊落库创建人 OWNER 与对方 MEMBER，未读数从 0 开始")
    void createSingleShouldInsertOwnerAndPeer() {
        stubInsertId(conversationMapper);
        CreateConversationRequest request = request("SINGLE", "忽略", List.of(2L));

        ImConversation conversation = service.create(USER_ID, request);

        assertEquals(ImConversationService.TYPE_SINGLE, conversation.getType());
        assertNull(conversation.getTitle());
        assertEquals(0L, conversation.getLastSeq());
        ArgumentCaptor<ImConversationMember> captor = ArgumentCaptor.forClass(ImConversationMember.class);
        verify(memberMapper, times(2)).insert(captor.capture());
        List<ImConversationMember> members = captor.getAllValues();
        assertEquals("OWNER", members.get(0).getRole());
        assertEquals("USER", members.get(0).getMemberType());
        assertEquals(USER_ID, members.get(0).getUserId());
        assertEquals(0L, members.get(0).getLastReadSeq());
        assertEquals("MEMBER", members.get(1).getRole());
        assertEquals(2L, members.get(1).getUserId());
    }

    @Test
    @DisplayName("create：单聊已存在时按 pair_key 返回既有会话，不再插入")
    void createSingleShouldReturnExistingByPairKey() {
        ImConversation existing = new ImConversation();
        existing.setId(CONVERSATION_ID);
        existing.setType(ImConversationService.TYPE_SINGLE);
        existing.setOwnerId(USER_ID);
        existing.setPairKey("S:2:10");
        when(conversationMapper.selectOne(any())).thenReturn(existing);

        ImConversation result = service.create(USER_ID, request("SINGLE", null, List.of(2L)));

        assertEquals(CONVERSATION_ID, result.getId());
        verify(conversationMapper, never()).insert(any(ImConversation.class));
        verify(memberMapper, never()).insert(any(ImConversationMember.class));
    }

    @Test
    @DisplayName("transfer：群主转让后双方角色互换并广播成员变更")
    void transferShouldSwapRoles() {
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, USER_ID)).thenReturn(member(USER_ID, "OWNER"));
        when(guard.requireMemberRecord(CONVERSATION_ID, 2L)).thenReturn(member(2L, "MEMBER"));

        service.transfer(CONVERSATION_ID, USER_ID, 2L);

        verify(conversationMapper).update(isNull(), any());
        verify(memberMapper, times(2)).update(isNull(), any());
        verify(broadcaster).broadcastMemberChanged(CONVERSATION_ID, "TRANSFER", 2L);
    }

    @Test
    @DisplayName("transfer：非群主拒绝转让")
    void transferShouldRejectNonOwner() {
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, USER_ID)).thenReturn(member(USER_ID, "ADMIN"));

        assertThrows(BizException.class, () -> service.transfer(CONVERSATION_ID, USER_ID, 2L));
        verify(memberMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("dissolve：群主解散群聊后逻辑删除会话并广播")
    void dissolveShouldDeleteConversation() {
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, USER_ID)).thenReturn(member(USER_ID, "OWNER"));

        service.dissolve(CONVERSATION_ID, USER_ID);

        verify(conversationMapper).deleteById(CONVERSATION_ID);
        verify(broadcaster).broadcastMemberChanged(CONVERSATION_ID, "DISSOLVED", USER_ID);
    }

    @Test
    @DisplayName("dissolve：非群主拒绝解散")
    void dissolveShouldRejectNonOwner() {
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(group());
        when(guard.requireMemberRecord(CONVERSATION_ID, USER_ID)).thenReturn(member(USER_ID, "MEMBER"));

        assertThrows(BizException.class, () -> service.dissolve(CONVERSATION_ID, USER_ID));
        verify(conversationMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("list：未读数 = lastSeq - lastReadSeq，并带最后一条消息摘要")
    void listShouldComputeUnreadAndAttachLastMessage() {
        ImConversationMember member = new ImConversationMember();
        member.setConversationId(CONVERSATION_ID);
        member.setUserId(USER_ID);
        member.setLastReadSeq(3L);
        when(memberMapper.selectList(any())).thenReturn(List.of(member));

        ImConversation conversation = new ImConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setType("GROUP");
        conversation.setTitle("研发群");
        conversation.setOwnerId(USER_ID);
        conversation.setLastSeq(5L);
        conversation.setLastMessageId(77L);
        conversation.setLastActiveAt(LocalDateTime.now());
        when(conversationMapper.selectBatchIds(any())).thenReturn(List.of(conversation));

        MessageVO summary = MessageVO.builder().id("77").content("最后一条").build();
        when(messageService.summaries(any())).thenReturn(Map.of(77L, summary));

        List<ConversationVO> result = service.list(USER_ID);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getUnread());
        assertNotNull(result.get(0).getLastMessage());
        assertEquals("最后一条", result.get(0).getLastMessage().getContent());
        assertEquals("研发群", result.get(0).getTitle());
    }

    @Test
    @DisplayName("list：没有会话时返回空列表且不查消息摘要")
    void listShouldReturnEmptyWithoutMember() {
        when(memberMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.list(USER_ID).isEmpty());
        verify(messageService, never()).summaries(any());
    }

    @Test
    @DisplayName("markRead：上报序号超过 lastSeq 时收敛到 lastSeq")
    void markReadShouldClampToLastSeq() {
        ImConversation conversation = new ImConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setLastSeq(10L);
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(conversation);

        service.markRead(CONVERSATION_ID, USER_ID, 99L);

        verify(memberMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("markRead：序号为 0 时不更新游标")
    void markReadShouldSkipZero() {
        ImConversation conversation = new ImConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setLastSeq(10L);
        when(guard.requireMember(CONVERSATION_ID, USER_ID)).thenReturn(conversation);

        service.markRead(CONVERSATION_ID, USER_ID, 0L);

        verify(memberMapper, never()).update(isNull(), any());
    }

    /**
     * 构造群聊会话
     *
     * @return 会话实体
     */
    private static ImConversation group() {
        ImConversation conversation = new ImConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setType(ImConversationService.TYPE_GROUP);
        conversation.setOwnerId(USER_ID);
        return conversation;
    }

    /**
     * 构造成员记录
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

    /**
     * 构造创建请求
     *
     * @param type      会话类型
     * @param title     标题
     * @param memberIds 成员ID
     * @return 创建请求
     */
    private static CreateConversationRequest request(String type, String title, List<Long> memberIds) {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setType(type);
        request.setTitle(title);
        request.setMemberIds(memberIds);
        return request;
    }

    /**
     * 让 mock 的 insert 回填主键，模拟 MyBatis-Plus 雪花ID
     *
     * @param mapper 会话 Mapper
     */
    private static void stubInsertId(ImConversationMapper mapper) {
        doAnswer(invocation -> {
            ImConversation conversation = invocation.getArgument(0);
            conversation.setId(CONVERSATION_ID);
            return 1;
        }).when(mapper).insert(any(ImConversation.class));
    }
}
