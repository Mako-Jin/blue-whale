package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.music.application.command.PlayProgressCommand;
import com.bluewhale.music.application.view.AudioStreamView;
import com.bluewhale.music.application.view.PlayTicketView;
import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.AudioStream;
import com.bluewhale.music.domain.model.CopyrightState;
import com.bluewhale.music.domain.model.PlayHistory;
import com.bluewhale.music.domain.model.PlayTicket;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongVisibility;
import com.bluewhale.music.domain.repository.AudioContentRepository;
import com.bluewhale.music.domain.repository.PlayCountRepository;
import com.bluewhale.music.domain.repository.PlayHistoryRepository;
import com.bluewhale.music.domain.repository.PlayTicketRepository;
import com.bluewhale.music.domain.repository.SongLikeRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 播放与音频流应用服务单元测试（M2-2 / M2-3 / M2-4）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PlayAppServiceImplTest {

    /** 测试租户 */
    private static final Long TENANT_ID = 1L;

    /** 测试用户 */
    private static final Long USER_ID = 7L;

    /** 测试歌曲 */
    private static final Long SONG_ID = 9L;

    @Mock
    private SongRepository songRepository;

    @Mock
    private PlayTicketRepository playTicketRepository;

    @Mock
    private PlayHistoryRepository playHistoryRepository;

    @Mock
    private SongLikeRepository songLikeRepository;

    @Mock
    private AudioContentRepository audioContentRepository;

    @Mock
    private PlayCountRepository playCountRepository;

    @Mock
    private MusicProperties musicProperties;

    @InjectMocks
    private PlayAppServiceImpl playAppService;

    /**
     * 已下架歌曲拒绝签发播放票据。
     */
    @Test
    void shouldRejectTicketForOfflineSong() {
        Song song = playableSong();
        song.setPublishState(PublishState.OFFLINE);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(song));
        BizException exception = assertThrows(BizException.class,
                () -> playAppService.issueTicket(SONG_ID, USER_ID, TENANT_ID));
        assertEquals(MusicErrorCode.SONG_NOT_FOUND, exception.getCode());
        verify(playTicketRepository, never()).issue(any(), any(Integer.class));
    }

    /**
     * 票据签发绑定租户、用户与歌曲，并返回含票据的流式地址。
     */
    @Test
    void shouldIssueTicketBoundToThreeElements() {
        when(musicProperties.getPlayTicketTtlSeconds()).thenReturn(120);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong()));
        PlayTicketView view = playAppService.issueTicket(SONG_ID, USER_ID, TENANT_ID);
        ArgumentCaptor<PlayTicket> captor = ArgumentCaptor.forClass(PlayTicket.class);
        verify(playTicketRepository).issue(captor.capture(), any(Integer.class));
        PlayTicket issued = captor.getValue();
        assertEquals(TENANT_ID, issued.getTenantId());
        assertEquals(USER_ID, issued.getUserId());
        assertEquals(SONG_ID, issued.getSongId());
        assertEquals(120, view.getExpiresIn());
        assertTrue(view.getStreamUrl().startsWith("/music/stream/" + SONG_ID + "?ticket="));
        assertTrue(view.getStreamUrl().contains(issued.getTicket()));
    }

    /**
     * 票据不存在或已过期时流式请求被拒绝。
     */
    @Test
    void shouldRejectStreamWithoutTicket() {
        when(playTicketRepository.find("expired")).thenReturn(Optional.empty());
        BizException exception = assertThrows(BizException.class,
                () -> playAppService.openStream(SONG_ID, "expired", USER_ID, TENANT_ID, null, null));
        assertEquals(MusicErrorCode.PLAY_TICKET_INVALID, exception.getCode());
        verify(audioContentRepository, never()).open(anyLong(), anyLong(), any(), any());
    }

    /**
     * 票据不可跨用户或跨歌曲复用。
     */
    @Test
    void shouldRejectTicketOfAnotherUser() {
        when(playTicketRepository.find("t1")).thenReturn(Optional.of(ticketOf(USER_ID, SONG_ID)));
        BizException exception = assertThrows(BizException.class,
                () -> playAppService.openStream(SONG_ID, "t1", 8L, TENANT_ID, null, null));
        assertEquals(MusicErrorCode.PLAY_TICKET_INVALID, exception.getCode());
        BizException mismatch = assertThrows(BizException.class,
                () -> playAppService.openStream(10L, "t1", USER_ID, TENANT_ID, null, null));
        assertEquals(MusicErrorCode.PLAY_TICKET_INVALID, mismatch.getCode());
    }

    /**
     * 版权下架后，即使票据仍在有效期内也必须拒绝播放。
     */
    @Test
    void shouldRejectStreamAfterTakedown() {
        Song song = playableSong();
        song.setCopyrightState(CopyrightState.TAKEDOWN);
        song.setPublishState(PublishState.OFFLINE);
        when(playTicketRepository.find("t1")).thenReturn(Optional.of(ticketOf(USER_ID, SONG_ID)));
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(song));
        BizException exception = assertThrows(BizException.class,
                () -> playAppService.openStream(SONG_ID, "t1", USER_ID, TENANT_ID, null, null));
        assertEquals(MusicErrorCode.SONG_NOT_FOUND, exception.getCode());
        verify(audioContentRepository, never()).open(anyLong(), anyLong(), any(), any());
    }

    /**
     * 合法票据透传 Range 并映射响应头与状态码。
     */
    @Test
    void shouldOpenStreamWithRange() {
        when(playTicketRepository.find("t1")).thenReturn(Optional.of(ticketOf(USER_ID, SONG_ID)));
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong()));
        when(audioContentRepository.open(10L, TENANT_ID, "bytes=0-1023", null)).thenReturn(
                new AudioStream(206, 1024L, "bytes 0-1023/2048", 0L, "audio/mpeg", "\"md5\"",
                        new ByteArrayInputStream(new byte[1024])));
        AudioStreamView view = playAppService.openStream(SONG_ID, "t1", USER_ID, TENANT_ID,
                "bytes=0-1023", null);
        assertEquals(206, view.getStatus());
        assertEquals(1024L, view.getContentLength());
        assertEquals("bytes 0-1023/2048", view.getContentRange());
        assertEquals("audio/mpeg", view.getContentType());
        assertEquals("\"md5\"", view.getEtag());
    }

    /**
     * 播放进度重复上报幂等：始终以同一用户同一歌曲写入。
     */
    @Test
    void shouldReportProgressIdempotently() {
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong()));
        PlayProgressCommand command = new PlayProgressCommand();
        command.setSongId(SONG_ID);
        command.setProgressMs(30_000L);
        command.setSource("SEARCH");
        playAppService.reportProgress(command, USER_ID);
        playAppService.reportProgress(command, USER_ID);
        ArgumentCaptor<PlayHistory> captor = ArgumentCaptor.forClass(PlayHistory.class);
        verify(playHistoryRepository, times(2)).saveProgress(captor.capture());
        captor.getAllValues().forEach(history -> {
            assertEquals(USER_ID, history.getUserId());
            assertEquals(SONG_ID, history.getSongId());
            assertEquals(30_000L, history.getProgressMs());
        });
    }

    /**
     * 重复收藏幂等：仓储返回未新增时不再累加收藏数。
     */
    @Test
    void shouldLikeIdempotently() {
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong()));
        when(songLikeRepository.add(USER_ID, SONG_ID)).thenReturn(true, false);
        playAppService.like(SONG_ID, USER_ID);
        playAppService.like(SONG_ID, USER_ID);
        verify(songRepository, times(1)).addLikeCount(SONG_ID, 1L);
    }

    /**
     * 构造可播放歌曲。
     *
     * @return 歌曲
     */
    private Song playableSong() {
        Song song = new Song();
        song.setId(SONG_ID);
        song.setUploaderId(USER_ID);
        song.setVisibility(SongVisibility.PUBLIC);
        song.setPublishState(PublishState.PUBLISHED);
        song.setCopyrightState(CopyrightState.VALID);
        song.setAudioFileId(10L);
        song.setTitle("晴天");
        song.setDurationMs(269_000L);
        return song;
    }

    /**
     * 构造绑定票据。
     *
     * @param userId 用户 ID
     * @param songId 歌曲 ID
     * @return 票据
     */
    private PlayTicket ticketOf(Long userId, Long songId) {
        PlayTicket ticket = new PlayTicket();
        ticket.setTicket("t1");
        ticket.setTenantId(TENANT_ID);
        ticket.setUserId(userId);
        ticket.setSongId(songId);
        return ticket;
    }
}
