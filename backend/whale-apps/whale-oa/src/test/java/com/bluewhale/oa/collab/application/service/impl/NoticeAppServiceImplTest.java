package com.bluewhale.oa.collab.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.collab.application.command.CollabCommands;
import com.bluewhale.oa.collab.application.view.CollabViews;
import com.bluewhale.oa.collab.domain.gateway.CollabUserDirectoryGateway;
import com.bluewhale.oa.collab.domain.model.Notice;
import com.bluewhale.oa.collab.domain.model.NoticeRecipient;
import com.bluewhale.oa.collab.domain.model.UserCandidate;
import com.bluewhale.oa.collab.domain.repository.CollabRepository;
import com.bluewhale.oa.common.OaErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公告应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class NoticeAppServiceImplTest {

    @Mock
    private CollabRepository repository;

    @Mock
    private CollabUserDirectoryGateway userDirectoryGateway;

    private NoticeAppServiceImpl noticeAppService;

    @BeforeEach
    void setUp() {
        noticeAppService = new NoticeAppServiceImpl(repository, userDirectoryGateway);
    }

    @Test
    @DisplayName("publish：指定用户公告缺少接收人时拒绝")
    void shouldRejectPublishWithoutRecipients() {
        when(repository.findNotice(1L)).thenReturn(draftNotice("USER"));
        when(repository.listRecipients(1L)).thenReturn(List.of());

        BizException exception = assertThrows(BizException.class,
                () -> noticeAppService.publishNotice(1L, 100L, "alice"));

        assertEquals(OaErrorCode.NOTICE_RECIPIENT_EMPTY, exception.getCode());
    }

    @Test
    @DisplayName("publish：全员公告从通讯录解析并落接收人快照")
    void shouldResolveAllRecipientsOnPublish() {
        when(repository.findNotice(1L)).thenReturn(draftNotice("ALL"));
        when(repository.listRecipients(1L)).thenReturn(List.of());
        when(userDirectoryGateway.listUsers(any(), any(), anyInt()))
                .thenReturn(List.of(candidate(101L, "Bob")));

        noticeAppService.publishNotice(1L, 100L, "alice");

        ArgumentCaptor<List<NoticeRecipient>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveRecipients(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(101L, captor.getValue().get(0).getUserId());

        ArgumentCaptor<Notice> noticeCaptor = ArgumentCaptor.forClass(Notice.class);
        verify(repository).updateNotice(noticeCaptor.capture());
        assertEquals("PUBLISHED", noticeCaptor.getValue().getStatus());
        assertEquals("alice", noticeCaptor.getValue().getPublisher());
    }

    @Test
    @DisplayName("publish：已有接收人快照时不再解析通讯录")
    void shouldKeepExistingRecipients() {
        when(repository.findNotice(1L)).thenReturn(draftNotice("USER"));
        when(repository.listRecipients(1L)).thenReturn(List.of(recipient(101L, 0)));

        noticeAppService.publishNotice(1L, 100L, "alice");

        verify(userDirectoryGateway, never()).listUsers(any(), any(), anyInt());
        verify(repository, never()).saveRecipients(any());
    }

    @Test
    @DisplayName("withdraw：草稿不可撤回")
    void shouldRejectWithdrawDraft() {
        when(repository.findNotice(1L)).thenReturn(draftNotice("ALL"));

        BizException exception = assertThrows(BizException.class,
                () -> noticeAppService.withdrawNotice(1L));

        assertEquals(OaErrorCode.NOTICE_STATUS_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("markRead：重复已读幂等不更新")
    void shouldIgnoreDuplicateRead() {
        Notice notice = draftNotice("ALL");
        notice.setStatus("PUBLISHED");
        when(repository.findNotice(1L)).thenReturn(notice);
        when(repository.findRecipient(1L, 101L)).thenReturn(recipient(101L, 1));

        noticeAppService.markRead(1L, 101L);

        verify(repository, never()).updateRecipient(any());
    }

    @Test
    @DisplayName("markRead：非接收人拒绝")
    void shouldRejectReadForNonRecipient() {
        Notice notice = draftNotice("ALL");
        notice.setStatus("PUBLISHED");
        when(repository.findNotice(1L)).thenReturn(notice);
        when(repository.findRecipient(1L, 102L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> noticeAppService.markRead(1L, 102L));

        assertEquals(OaErrorCode.NOTICE_RECIPIENT_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("stats：汇总接收与已读数量")
    void shouldCountReadStats() {
        Notice notice = draftNotice("ALL");
        notice.setStatus("PUBLISHED");
        when(repository.findNotice(1L)).thenReturn(notice);
        when(repository.listRecipients(1L)).thenReturn(List.of(recipient(101L, 1), recipient(102L, 0)));

        CollabViews.NoticeDetailView detail = noticeAppService.readStats(1L);

        assertEquals(2L, detail.getRecipientCount());
        assertEquals(1L, detail.getReadCount());
    }

    private static Notice draftNotice(String scopeType) {
        Notice notice = new Notice();
        notice.setId(1L);
        notice.setTitle("放假通知");
        notice.setContent("内容");
        notice.setScopeType(scopeType);
        notice.setScopeValue("DEPT-1");
        notice.setStatus("DRAFT");
        return notice;
    }

    private static NoticeRecipient recipient(Long userId, int readFlag) {
        NoticeRecipient recipient = new NoticeRecipient();
        recipient.setId(userId);
        recipient.setNoticeId(1L);
        recipient.setUserId(userId);
        recipient.setReadFlag(readFlag);
        return recipient;
    }

    private static UserCandidate candidate(Long userId, String nickname) {
        UserCandidate candidate = new UserCandidate();
        candidate.setUserId(userId);
        candidate.setUsername("user" + userId);
        candidate.setNickname(nickname);
        return candidate;
    }
}
