package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.AppealQueryCommand;
import com.bluewhale.music.application.command.AppealReviewCommand;
import com.bluewhale.music.application.command.AppealSubmitCommand;
import com.bluewhale.music.application.view.SongAppealView;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.AppealStatus;
import com.bluewhale.music.domain.model.CopyrightState;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongAppeal;
import com.bluewhale.music.domain.model.SongVisibility;
import com.bluewhale.music.domain.repository.HomeCacheRepository;
import com.bluewhale.music.domain.repository.SongAppealRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 版权下架申诉应用服务单元测试：提交越权口径 / 重复申诉 / 复核流转与版权恢复。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SongAppealAppServiceImplTest {

    /** 歌曲 ID */
    private static final Long SONG_ID = 9L;

    /** 申诉 ID */
    private static final Long APPEAL_ID = 100L;

    /** 申诉人（作品上传者） */
    private static final Long APPLICANT_ID = 7L;

    /** 非上传者用户 */
    private static final Long OTHER_USER_ID = 8L;

    /** 复核人（运营） */
    private static final Long REVIEWER_ID = 1L;

    @Mock
    private SongAppealRepository songAppealRepository;

    @Mock
    private SongRepository songRepository;

    @Mock
    private HomeCacheRepository homeCacheRepository;

    @InjectMocks
    private SongAppealAppServiceImpl songAppealAppService;

    /**
     * 上传者可对自己版权下架的作品提交申诉，初始状态为待复核。
     */
    @Test
    void shouldSubmitAppealForTakenDownOwnSong() {
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(takenDownSong(APPLICANT_ID)));
        when(songAppealRepository.existsPending(SONG_ID)).thenReturn(false);

        SongAppealView view = songAppealAppService.submit(SONG_ID, submitCommand("原创作品，请复核"), APPLICANT_ID);

        ArgumentCaptor<SongAppeal> captor = ArgumentCaptor.forClass(SongAppeal.class);
        verify(songAppealRepository).insert(captor.capture());
        SongAppeal inserted = captor.getValue();
        assertEquals(SONG_ID, inserted.getSongId());
        assertEquals(APPLICANT_ID, inserted.getApplicantId());
        assertEquals("原创作品，请复核", inserted.getReason());
        assertEquals(AppealStatus.PENDING, inserted.getStatus());
        assertEquals("PENDING", view.getStatus());
        assertEquals("晴天", view.getSongTitle());
    }

    /**
     * 非版权下架状态的作品不允许申诉。
     */
    @Test
    void shouldRejectSubmitWhenSongNotTakenDown() {
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(publishedSong(APPLICANT_ID)));
        BizException exception = assertThrows(BizException.class,
                () -> songAppealAppService.submit(SONG_ID, submitCommand("请复核"), APPLICANT_ID));
        assertEquals(MusicErrorCode.APPEAL_NOT_ALLOWED, exception.getCode());
        verify(songAppealRepository, never()).insert(any());
    }

    /**
     * 可见但非本人作品：按无权操作拒绝（40301），不进入申诉流程。
     */
    @Test
    void shouldRejectSubmitByOtherUserOnVisibleSong() {
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(publishedSong(APPLICANT_ID)));
        BizException exception = assertThrows(BizException.class,
                () -> songAppealAppService.submit(SONG_ID, submitCommand("请复核"), OTHER_USER_ID));
        assertEquals(MusicErrorCode.SONG_NOT_OWNED, exception.getCode());
        verify(songAppealRepository, never()).insert(any());
    }

    /**
     * 不可见（私有 / 跨租户）作品：按不存在处理（40403），不泄露存在性。
     */
    @Test
    void shouldHideInvisibleSongFromOtherUser() {
        Song privateSong = publishedSong(APPLICANT_ID);
        privateSong.setVisibility(SongVisibility.PRIVATE);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(privateSong));
        BizException exception = assertThrows(BizException.class,
                () -> songAppealAppService.submit(SONG_ID, submitCommand("请复核"), OTHER_USER_ID));
        assertEquals(MusicErrorCode.SONG_NOT_FOUND, exception.getCode());
    }

    /**
     * 同一作品已有待复核申诉时不可重复提交。
     */
    @Test
    void shouldRejectDuplicatePendingAppeal() {
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(takenDownSong(APPLICANT_ID)));
        when(songAppealRepository.existsPending(SONG_ID)).thenReturn(true);
        BizException exception = assertThrows(BizException.class,
                () -> songAppealAppService.submit(SONG_ID, submitCommand("请复核"), APPLICANT_ID));
        assertEquals(MusicErrorCode.APPEAL_STATE_CONFLICT, exception.getCode());
        verify(songAppealRepository, never()).insert(any());
    }

    /**
     * 申诉理由为空时由领域校验拒绝，且参数校验优先于「已有待复核」判重。
     */
    @Test
    void shouldRejectBlankReason() {
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(takenDownSong(APPLICANT_ID)));
        // 即使该作品已有待复核申诉，空理由也应先返回参数错误 40022，而不是 40902
        lenient().when(songAppealRepository.existsPending(SONG_ID)).thenReturn(true);
        BizException exception = assertThrows(BizException.class,
                () -> songAppealAppService.submit(SONG_ID, submitCommand("   "), APPLICANT_ID));
        assertEquals(MusicErrorCode.APPEAL_REASON_INVALID, exception.getCode());
        verify(songAppealRepository, never()).insert(any());
    }

    /**
     * 申诉通过：申诉置为通过，歌曲版权恢复为有效并保持下架。
     */
    @Test
    void shouldApproveAppealAndRestoreCopyright() {
        SongAppeal appeal = pendingAppeal();
        Song song = takenDownSong(APPLICANT_ID);
        when(songAppealRepository.findById(APPEAL_ID)).thenReturn(Optional.of(appeal));
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(song));

        SongAppealView view = songAppealAppService.approve(APPEAL_ID, reviewCommand("复核通过"), REVIEWER_ID);

        assertEquals("APPROVED", view.getStatus());
        assertEquals(REVIEWER_ID, view.getReviewerId());
        assertEquals("复核通过", view.getReviewOpinion());
        assertNotNull(view.getReviewedAt());
        assertEquals(CopyrightState.VALID, song.getCopyrightState());
        assertEquals(PublishState.OFFLINE, song.getPublishState());
        assertNull(song.getTakedownReason());
        verify(songRepository).update(song);
        verify(songAppealRepository).update(appeal);
    }

    /**
     * 已复核的申诉不可重复处理，且不会再次改动歌曲状态。
     */
    @Test
    void shouldRejectSecondReview() {
        SongAppeal appeal = pendingAppeal();
        appeal.approve(REVIEWER_ID, "已通过");
        when(songAppealRepository.findById(APPEAL_ID)).thenReturn(Optional.of(appeal));

        BizException exception = assertThrows(BizException.class,
                () -> songAppealAppService.reject(APPEAL_ID, reviewCommand("驳回"), REVIEWER_ID));
        assertEquals(MusicErrorCode.APPEAL_STATE_CONFLICT, exception.getCode());
        verify(songRepository, never()).update(any());
        verify(songAppealRepository, never()).update(any());
    }

    /**
     * 申诉不存在：按不存在拒绝（40407）。
     */
    @Test
    void shouldRejectReviewOnMissingAppeal() {
        when(songAppealRepository.findById(APPEAL_ID)).thenReturn(Optional.empty());
        BizException exception = assertThrows(BizException.class,
                () -> songAppealAppService.approve(APPEAL_ID, reviewCommand("通过"), REVIEWER_ID));
        assertEquals(MusicErrorCode.APPEAL_NOT_FOUND, exception.getCode());
    }

    /**
     * 申诉驳回：仅更新申诉状态，歌曲保持版权下架。
     */
    @Test
    void shouldRejectAppealWithoutRestoringSong() {
        SongAppeal appeal = pendingAppeal();
        when(songAppealRepository.findById(APPEAL_ID)).thenReturn(Optional.of(appeal));
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(takenDownSong(APPLICANT_ID)));

        SongAppealView view = songAppealAppService.reject(APPEAL_ID, reviewCommand("请补充权属证明"), REVIEWER_ID);

        assertEquals("REJECTED", view.getStatus());
        assertEquals("请补充权属证明", view.getReviewOpinion());
        verify(songAppealRepository).update(appeal);
        verify(songRepository, never()).update(any());
    }

    /**
     * 复核分页填充歌曲标题并返回总数。
     */
    @Test
    void shouldPageAuditWithSongTitle() {
        SongAppeal first = pendingAppeal();
        SongAppeal second = pendingAppeal();
        second.setId(200L);
        when(songAppealRepository.pageAudit(any(), any(), anyLong(), anyLong())).thenReturn(List.of(first, second));
        when(songRepository.findByIds(any())).thenReturn(List.of(takenDownSong(APPLICANT_ID)));
        when(songAppealRepository.countAudit(any(), any())).thenReturn(2L);

        AppealQueryCommand command = new AppealQueryCommand();
        PageResult<SongAppealView> page = songAppealAppService.pageAudit(command);

        assertEquals(2L, page.getTotal());
        assertEquals(2, page.getRecords().size());
        assertTrue(page.getRecords().stream().allMatch(view -> "晴天".equals(view.getSongTitle())));
    }

    /**
     * 构造申诉提交命令。
     *
     * @param reason 申诉理由
     * @return 提交命令
     */
    private AppealSubmitCommand submitCommand(String reason) {
        AppealSubmitCommand command = new AppealSubmitCommand();
        command.setReason(reason);
        return command;
    }

    /**
     * 构造复核命令。
     *
     * @param opinion 审核意见
     * @return 复核命令
     */
    private AppealReviewCommand reviewCommand(String opinion) {
        AppealReviewCommand command = new AppealReviewCommand();
        command.setOpinion(opinion);
        return command;
    }

    /**
     * 构造待复核申诉。
     *
     * @return 申诉
     */
    private SongAppeal pendingAppeal() {
        SongAppeal appeal = SongAppeal.create(SONG_ID, APPLICANT_ID, "原创作品，请复核");
        appeal.setId(APPEAL_ID);
        return appeal;
    }

    /**
     * 构造版权下架状态的公开作品。
     *
     * @param uploaderId 上传者用户 ID
     * @return 歌曲
     */
    private Song takenDownSong(Long uploaderId) {
        Song song = new Song();
        song.setId(SONG_ID);
        song.setUploaderId(uploaderId);
        song.setTitle("晴天");
        song.setVisibility(SongVisibility.PUBLIC);
        song.setPublishState(PublishState.OFFLINE);
        song.setCopyrightState(CopyrightState.TAKEDOWN);
        song.setTakedownReason("版权争议");
        return song;
    }

    /**
     * 构造已上架的公开作品。
     *
     * @param uploaderId 上传者用户 ID
     * @return 歌曲
     */
    private Song publishedSong(Long uploaderId) {
        Song song = new Song();
        song.setId(SONG_ID);
        song.setUploaderId(uploaderId);
        song.setTitle("晴天");
        song.setVisibility(SongVisibility.PUBLIC);
        song.setPublishState(PublishState.PUBLISHED);
        song.setCopyrightState(CopyrightState.VALID);
        return song;
    }
}
