package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.video.application.command.PlayProgressCommand;
import com.bluewhale.video.application.support.PublicContentViewAssembler;
import com.bluewhale.video.application.view.PlayTicketBatchView;
import com.bluewhale.video.application.view.PlayTicketView;
import com.bluewhale.video.application.view.VideoStreamView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.ContentVisibility;
import com.bluewhale.video.domain.model.CopyrightState;
import com.bluewhale.video.domain.model.PlayTicket;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.model.VideoStream;
import com.bluewhale.video.domain.model.WatchHistory;
import com.bluewhale.video.domain.repository.ContentFavoriteRepository;
import com.bluewhale.video.domain.repository.ContentLikeRepository;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.ContentStreamRepository;
import com.bluewhale.video.domain.repository.PlayCountRepository;
import com.bluewhale.video.domain.repository.PlayTicketRepository;
import com.bluewhale.video.domain.repository.PlayableCacheRepository;
import com.bluewhale.video.domain.repository.WatchHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 播放与互动应用服务单元测试（M1-3 / M1-4）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PlayAppServiceImplTest {

    /** 测试租户 */
    private static final Long TENANT_ID = 1L;

    /** 测试用户 */
    private static final Long USER_ID = 7L;

    /** 测试内容 */
    private static final Long CONTENT_ID = 9L;

    /** 测试视频文件 */
    private static final Long FILE_ID = 10L;

    /** 不可播内容 */
    private static final Long DRAFT_ID = 11L;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentStreamRepository contentStreamRepository;

    @Mock
    private PlayTicketRepository playTicketRepository;

    @Mock
    private PlayableCacheRepository playableCacheRepository;

    @Mock
    private PlayCountRepository playCountRepository;

    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    @Mock
    private ContentLikeRepository contentLikeRepository;

    @Mock
    private ContentFavoriteRepository contentFavoriteRepository;

    @Mock
    private PublicContentViewAssembler contentViewAssembler;

    @Mock
    private VideoProperties videoProperties;

    @InjectMocks
    private PlayAppServiceImpl playAppService;

    /**
     * 票据签发绑定租户、用户与内容，并返回不含文件 ID 的流式地址。
     */
    @Test
    void shouldIssueTicketBoundToThreeElements() {
        when(videoProperties.getPlayTicketTtlSeconds()).thenReturn(300);
        when(videoProperties.getPlayTicketRefreshSeconds()).thenReturn(240);
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(playableContent()));
        when(watchHistoryRepository.find(USER_ID, CONTENT_ID)).thenReturn(Optional.empty());

        PlayTicketView view = playAppService.issueTicket(CONTENT_ID, USER_ID, TENANT_ID);

        ArgumentCaptor<PlayTicket> captor = ArgumentCaptor.forClass(PlayTicket.class);
        verify(playTicketRepository).issue(captor.capture(), anyInt());
        PlayTicket issued = captor.getValue();
        assertEquals(TENANT_ID, issued.getTenantId());
        assertEquals(USER_ID, issued.getUserId());
        assertEquals(CONTENT_ID, issued.getContentId());
        assertEquals(300, view.getExpiresIn());
        assertEquals(240, view.getRefreshIn());
        assertEquals(0L, view.getProgressMs());
        assertEquals("/video/stream/" + CONTENT_ID + "?ticket=" + issued.getTicket(), view.getStreamUrl());
        assertTrue(!view.getStreamUrl().contains("fileId"));
    }

    /**
     * 作者本人的草稿：内容存在且对自己可见，但未发布 → 按「不可播放」拒绝（40303）。
     *
     * <p>与「他人草稿」区分：后者连存在性都不能暴露，才返回 40401。</p>
     */
    @Test
    void shouldRejectTicketForOwnDraftAsNotPlayable() {
        when(contentRepository.findById(DRAFT_ID)).thenReturn(Optional.of(draftContent()));
        BizException exception = assertThrows(BizException.class,
                () -> playAppService.issueTicket(DRAFT_ID, USER_ID, TENANT_ID));
        assertEquals(VideoErrorCode.CONTENT_NOT_PLAYABLE, exception.getCode());
        verify(playTicketRepository, never()).issue(any(), anyInt());
    }

    /**
     * 他人草稿对当前用户不可见，返回 40401 避免泄露内容存在性。
     */
    @Test
    void shouldRejectTicketForOthersDraftAsNotFound() {
        Content othersDraft = draftContent();
        othersDraft.setUploaderId(USER_ID + 1);
        when(contentRepository.findById(DRAFT_ID)).thenReturn(Optional.of(othersDraft));
        BizException exception = assertThrows(BizException.class,
                () -> playAppService.issueTicket(DRAFT_ID, USER_ID, TENANT_ID));
        assertEquals(VideoErrorCode.CONTENT_NOT_FOUND, exception.getCode());
        verify(playTicketRepository, never()).issue(any(), anyInt());
    }

    /**
     * 批量票据跳过不可播内容，并在结果中回传被拒绝的内容 ID。
     */
    @Test
    void shouldSkipUnplayableContentInBatch() {
        when(contentRepository.findByIds(List.of(CONTENT_ID, DRAFT_ID)))
                .thenReturn(List.of(playableContent(), draftContent()));
        when(videoProperties.getPlayTicketTtlSeconds()).thenReturn(300);
        when(videoProperties.getPlayTicketRefreshSeconds()).thenReturn(240);
        when(watchHistoryRepository.find(USER_ID, CONTENT_ID)).thenReturn(Optional.of(history()));

        PlayTicketBatchView view = playAppService.issueTickets(List.of(CONTENT_ID, DRAFT_ID), USER_ID, TENANT_ID);

        assertEquals(1, view.getTickets().size());
        assertEquals(CONTENT_ID, view.getTickets().get(0).getContentId());
        assertEquals(60_000L, view.getTickets().get(0).getProgressMs());
        assertEquals(List.of(DRAFT_ID), view.getRejectedContentIds());
        verify(playTicketRepository, times(1)).issue(any(), anyInt());
    }

    /**
     * 空内容 ID 列表返回空批量结果，不触发仓储查询。
     */
    @Test
    void shouldReturnEmptyBatchForBlankIds() {
        PlayTicketBatchView view = playAppService.issueTickets(null, USER_ID, TENANT_ID);
        assertTrue(view.getTickets().isEmpty());
        assertTrue(view.getRejectedContentIds().isEmpty());
        verify(playTicketRepository, never()).issue(any(), anyInt());
    }

    /**
     * 缺失或失效票据时拒绝打开视频流。
     */
    @Test
    void shouldRejectStreamWithoutTicket() {
        when(playTicketRepository.find("expired")).thenReturn(Optional.empty());
        BizException exception = assertThrows(BizException.class,
                () -> playAppService.openStream(CONTENT_ID, "expired", USER_ID, TENANT_ID, null, null));
        assertEquals(VideoErrorCode.PLAY_TICKET_INVALID, exception.getCode());

        assertEquals(VideoErrorCode.PLAY_TICKET_INVALID,
                assertThrows(BizException.class,
                        () -> playAppService.openStream(CONTENT_ID, "  ", USER_ID, TENANT_ID, null, null)).getCode());
        verify(contentStreamRepository, never()).open(anyLong(), anyLong(), any(), any());
    }

    /**
     * 票据不可跨用户或跨内容复用。
     */
    @Test
    void shouldRejectTicketOfAnotherUserOrContent() {
        when(playTicketRepository.find("t1")).thenReturn(Optional.of(ticketOf(8L, CONTENT_ID)));
        assertEquals(VideoErrorCode.PLAY_TICKET_INVALID,
                assertThrows(BizException.class,
                        () -> playAppService.openStream(CONTENT_ID, "t1", USER_ID, TENANT_ID, null, null)).getCode());

        when(playTicketRepository.find("t1")).thenReturn(Optional.of(ticketOf(USER_ID, DRAFT_ID)));
        assertEquals(VideoErrorCode.PLAY_TICKET_INVALID,
                assertThrows(BizException.class,
                        () -> playAppService.openStream(CONTENT_ID, "t1", USER_ID, TENANT_ID, null, null)).getCode());
        verify(contentStreamRepository, never()).open(anyLong(), anyLong(), any(), any());
    }

    /**
     * 可播状态缓存标记不可播时直接拒绝，不访问内容与文件服务。
     */
    @Test
    void shouldRejectWhenPlayableCacheSaysNo() {
        when(playTicketRepository.find("t1")).thenReturn(Optional.of(ticketOf(USER_ID, CONTENT_ID)));
        when(playableCacheRepository.find(CONTENT_ID)).thenReturn(false);
        BizException exception = assertThrows(BizException.class,
                () -> playAppService.openStream(CONTENT_ID, "t1", USER_ID, TENANT_ID, null, null));
        assertEquals(VideoErrorCode.CONTENT_NOT_PLAYABLE, exception.getCode());
        verify(contentRepository, never()).findById(anyLong());
        verify(contentStreamRepository, never()).open(anyLong(), anyLong(), any(), any());
    }

    /**
     * 合法票据透传 Range，命中 200 / 206 时累加播放次数并写入可播缓存。
     */
    @Test
    void shouldOpenStreamAndCountPlay() {
        when(playTicketRepository.find("t1")).thenReturn(Optional.of(ticketOf(USER_ID, CONTENT_ID)));
        when(playableCacheRepository.find(CONTENT_ID)).thenReturn(null);
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(playableContent()));
        when(contentStreamRepository.open(FILE_ID, TENANT_ID, "bytes=0-1023", "\"md5\"")).thenReturn(
                new VideoStream(206, 1024L, "bytes 0-1023/2048", 0L, "video/mp4", "\"md5\"", null));

        VideoStreamView view = playAppService.openStream(CONTENT_ID, "t1", USER_ID, TENANT_ID,
                "bytes=0-1023", "\"md5\"");

        assertEquals(206, view.getStatus());
        assertEquals(1024L, view.getContentLength());
        assertEquals("bytes 0-1023/2048", view.getContentRange());
        assertEquals("video/mp4", view.getContentType());
        verify(playableCacheRepository).save(CONTENT_ID, true);
        verify(playCountRepository).increment(CONTENT_ID);
    }

    /**
     * 416 越界响应不计入播放次数。
     */
    @Test
    void shouldNotCountPlayOnRangeNotSatisfiable() {
        when(playTicketRepository.find("t1")).thenReturn(Optional.of(ticketOf(USER_ID, CONTENT_ID)));
        when(playableCacheRepository.find(CONTENT_ID)).thenReturn(null);
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(playableContent()));
        when(contentStreamRepository.open(FILE_ID, TENANT_ID, "bytes=99999-", null)).thenReturn(
                new VideoStream(416, 0L, "bytes */2048", 2048L, "video/mp4", "\"md5\"", null));

        VideoStreamView view = playAppService.openStream(CONTENT_ID, "t1", USER_ID, TENANT_ID, "bytes=99999-", null);

        assertEquals(416, view.getStatus());
        assertEquals(2048L, view.getContentRangeTotal());
        verify(playCountRepository, never()).increment(anyLong());
    }

    /**
     * 进度超过内容时长时拒绝写入历史。
     */
    @Test
    void shouldRejectProgressBeyondDuration() {
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(playableContent()));
        PlayProgressCommand command = new PlayProgressCommand();
        command.setContentId(CONTENT_ID);
        command.setProgressMs(3_600_001L);
        assertEquals(VideoErrorCode.PLAY_PROGRESS_INVALID,
                assertThrows(BizException.class, () -> playAppService.reportProgress(command, USER_ID)).getCode());
        verify(watchHistoryRepository, never()).saveProgress(any());
    }

    /**
     * 合法进度写入历史，来源为空时回落默认来源。
     */
    @Test
    void shouldPersistProgressWithDefaultSource() {
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(playableContent()));
        PlayProgressCommand command = new PlayProgressCommand();
        command.setContentId(CONTENT_ID);
        command.setProgressMs(60_000L);

        playAppService.reportProgress(command, USER_ID);

        ArgumentCaptor<WatchHistory> captor = ArgumentCaptor.forClass(WatchHistory.class);
        verify(watchHistoryRepository).saveProgress(captor.capture());
        WatchHistory history = captor.getValue();
        assertEquals(USER_ID, history.getUserId());
        assertEquals(CONTENT_ID, history.getContentId());
        assertEquals(60_000L, history.getProgressMs());
        assertEquals(3_600_000L, history.getDurationMs());
        assertEquals("MY", history.getSource());
    }

    /**
     * 重复点赞幂等：仓储返回未新增时不再累加点赞数。
     */
    @Test
    void shouldLikeIdempotently() {
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(playableContent()));
        when(contentLikeRepository.add(USER_ID, CONTENT_ID)).thenReturn(true, false);
        playAppService.like(CONTENT_ID, USER_ID);
        playAppService.like(CONTENT_ID, USER_ID);
        verify(contentRepository, times(1)).addLikeCount(CONTENT_ID, 1L);
    }

    /**
     * 重复收藏幂等：仓储返回未新增时不再累加收藏数。
     */
    @Test
    void shouldFavoriteIdempotently() {
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(playableContent()));
        when(contentFavoriteRepository.add(USER_ID, CONTENT_ID)).thenReturn(true, false);
        playAppService.favorite(CONTENT_ID, USER_ID);
        playAppService.favorite(CONTENT_ID, USER_ID);
        verify(contentRepository, times(1)).addFavoriteCount(CONTENT_ID, 1L);
    }

    /**
     * 删除最近观看记录按用户 + 内容精确删除（幂等）。
     */
    @Test
    void shouldRemoveHistoryByIdempotentDelete() {
        playAppService.removeHistory(CONTENT_ID, USER_ID);
        verify(watchHistoryRepository).delete(USER_ID, CONTENT_ID);
    }

    /**
     * 构造可播放内容。
     *
     * @return 内容
     */
    private Content playableContent() {
        Content content = new Content();
        content.setId(CONTENT_ID);
        content.setContentType(ContentType.VIDEO);
        content.setTitle("深海纪录片");
        content.setUploaderId(USER_ID);
        content.setVisibility(ContentVisibility.PUBLIC);
        content.setPublishState(PublishState.PUBLISHED);
        content.setCopyrightState(CopyrightState.AUTHORIZED);
        content.setVideoFileId(FILE_ID);
        content.setDurationMs(3_600_000L);
        return content;
    }

    /**
     * 构造草稿内容。
     *
     * @return 草稿内容
     */
    private Content draftContent() {
        Content content = playableContent();
        content.setId(DRAFT_ID);
        content.setPublishState(PublishState.DRAFT);
        return content;
    }

    /**
     * 构造观看历史。
     *
     * @return 观看历史
     */
    private WatchHistory history() {
        WatchHistory history = new WatchHistory();
        history.setUserId(USER_ID);
        history.setContentId(CONTENT_ID);
        history.setProgressMs(60_000L);
        history.setDurationMs(3_600_000L);
        return history;
    }

    /**
     * 构造绑定票据。
     *
     * @param userId 用户 ID
     * @param contentId 内容 ID
     * @return 票据
     */
    private PlayTicket ticketOf(Long userId, Long contentId) {
        PlayTicket ticket = new PlayTicket();
        ticket.setTicket("t1");
        ticket.setTenantId(TENANT_ID);
        ticket.setUserId(userId);
        ticket.setContentId(contentId);
        return ticket;
    }
}
