package com.bluewhale.music.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 歌曲聚合根状态机与可见性规则单元测试。
 *
 * @author BlueWhale
 */
class SongTest {

    /**
     * 信息完整的歌曲可以上架，并刷新上架时间。
     */
    @Test
    void shouldPublishReadySong() {
        Song song = readySong();
        song.publish();
        assertEquals(PublishState.PUBLISHED, song.getPublishState());
        assertNotNull(song.getPublishedAt());
        assertTrue(song.playable());
    }

    /**
     * 缺失音频或时长的歌曲不能上架。
     */
    @Test
    void shouldRejectPublishWithoutAudioOrDuration() {
        Song song = readySong();
        song.setAudioFileId(null);
        BizException exception = assertThrows(BizException.class, song::publish);
        assertEquals(MusicErrorCode.SONG_NOT_READY, exception.getCode());

        Song noDuration = readySong();
        noDuration.setDurationMs(0L);
        assertEquals(MusicErrorCode.SONG_NOT_READY,
                assertThrows(BizException.class, noDuration::publish).getCode());
    }

    /**
     * 版权下架后不得再次上架。
     */
    @Test
    void shouldRejectPublishAfterTakedown() {
        Song song = readySong();
        song.takedown("版权争议");
        BizException exception = assertThrows(BizException.class, song::publish);
        assertEquals(MusicErrorCode.SONG_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 版权下架强制切到 OFFLINE 与 TAKEDOWN，并记录原因。
     */
    @Test
    void shouldForceOfflineOnTakedown() {
        Song song = readySong();
        song.publish();
        song.takedown("  侵权投诉  ");
        assertEquals(PublishState.OFFLINE, song.getPublishState());
        assertEquals(CopyrightState.TAKEDOWN, song.getCopyrightState());
        assertEquals("侵权投诉", song.getTakedownReason());
        assertFalse(song.playable());
    }

    /**
     * 版权下架必须填写原因。
     */
    @Test
    void shouldRejectTakedownWithoutReason() {
        Song song = readySong();
        BizException exception = assertThrows(BizException.class, () -> song.takedown("  "));
        assertEquals(MusicErrorCode.TAKEDOWN_REASON_REQUIRED, exception.getCode());
    }

    /**
     * 草稿不能直接下架。
     */
    @Test
    void shouldRejectOfflineDraft() {
        Song song = readySong();
        BizException exception = assertThrows(BizException.class, song::offline);
        assertEquals(MusicErrorCode.SONG_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 自听作品仅上传者可见，运营可见全部。
     */
    @Test
    void shouldLimitPrivateSongVisibility() {
        Song song = readySong();
        song.setVisibility(SongVisibility.PRIVATE);
        song.publish();
        assertTrue(song.visibleTo(song.getUploaderId(), false));
        assertFalse(song.visibleTo(999L, false));
        assertTrue(song.visibleTo(999L, true));
    }

    /**
     * 非本人操作作品应被拒绝。
     */
    @Test
    void shouldRejectNonOwnerOperation() {
        Song song = readySong();
        BizException exception = assertThrows(BizException.class, () -> song.assertOwnedBy(999L));
        assertEquals(MusicErrorCode.SONG_NOT_OWNED, exception.getCode());
    }

    /**
     * 构造一首信息完整、可发布的歌曲。
     *
     * @return 歌曲
     */
    private Song readySong() {
        Song song = new Song();
        song.setUploaderId(1L);
        song.setArtistId(2L);
        song.setTitle("晴天");
        song.setAudioFileId(3L);
        song.setDurationMs(269000L);
        song.setVisibility(SongVisibility.PUBLIC);
        song.setPublishState(PublishState.DRAFT);
        song.setCopyrightState(CopyrightState.DECLARED);
        return song;
    }
}
