package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.music.application.command.AppealQueryCommand;
import com.bluewhale.music.application.command.AppealReviewCommand;
import com.bluewhale.music.application.command.AppealSubmitCommand;
import com.bluewhale.music.application.service.SongAppealAppService;
import com.bluewhale.music.application.view.SongAppealView;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.AppealStatus;
import com.bluewhale.music.domain.model.CopyrightState;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongAppeal;
import com.bluewhale.music.domain.repository.HomeCacheRepository;
import com.bluewhale.music.domain.repository.SongAppealRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 版权下架申诉应用服务实现。
 *
 * <p>提交侧遵循「不泄露存在性」口径：跨租户 / 不可见作品一律按不存在处理（40403），
 * 仅作品上传者本人可申诉，且作品必须处于版权下架状态。复核通过后歌曲版权恢复为
 * {@link CopyrightState#VALID} 并保持 {@code OFFLINE}，需运营显式重新上架，
 * 避免申诉通过即自动复播。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SongAppealAppServiceImpl implements SongAppealAppService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    private final SongAppealRepository songAppealRepository;

    private final SongRepository songRepository;

    private final HomeCacheRepository homeCacheRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SongAppealView submit(Long songId, AppealSubmitCommand command, Long userId) {
        Song song = requireAppealableSong(songId, userId);
        // 先做请求体校验（40022），再判重（40902）：空理由属于参数错误，不应被重复申诉掩盖
        SongAppeal appeal = SongAppeal.create(songId, userId, command.getReason());
        if (songAppealRepository.existsPending(songId)) {
            throw MusicErrorCode.exception(MusicErrorCode.APPEAL_STATE_CONFLICT);
        }
        songAppealRepository.insert(appeal);
        SongAppealView view = toView(appeal);
        view.setSongTitle(song.getTitle());
        return view;
    }

    @Override
    public PageResult<SongAppealView> pageAudit(AppealQueryCommand command) {
        long pageNo = clampCurrent(command.getCurrent());
        long pageSize = clampSize(command.getSize());
        AppealStatus status = parseStatus(command.getStatus());
        List<SongAppeal> appeals = songAppealRepository.pageAudit(command.getSongId(), status,
                (pageNo - 1) * pageSize, pageSize);
        Map<Long, Song> songMap = songRepository.findByIds(
                appeals.stream().map(SongAppeal::getSongId).distinct().toList()).stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));
        List<SongAppealView> records = appeals.stream().map(appeal -> {
            SongAppealView view = toView(appeal);
            Song song = songMap.get(appeal.getSongId());
            view.setSongTitle(song == null ? null : song.getTitle());
            return view;
        }).toList();
        return PageResult.of(records,
                songAppealRepository.countAudit(command.getSongId(), status), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SongAppealView approve(Long id, AppealReviewCommand command, Long reviewerId) {
        SongAppeal appeal = require(id);
        Song song = songRepository.findById(appeal.getSongId())
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.SONG_NOT_FOUND));
        appeal.approve(reviewerId, opinionOf(command));
        song.restoreCopyright();
        songRepository.update(song);
        songAppealRepository.update(appeal);
        evictHomeCache();
        SongAppealView view = toView(appeal);
        view.setSongTitle(song.getTitle());
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SongAppealView reject(Long id, AppealReviewCommand command, Long reviewerId) {
        SongAppeal appeal = require(id);
        appeal.reject(reviewerId, opinionOf(command));
        songAppealRepository.update(appeal);
        SongAppealView view = toView(appeal);
        songRepository.findById(appeal.getSongId()).ifPresent(song -> view.setSongTitle(song.getTitle()));
        return view;
    }

    /**
     * 加载可申诉歌曲：仅上传者本人可申诉，未签署或非版权下架状态拒绝。
     *
     * <p>非本人时按「不泄露存在性」处理：作品对当前用户不可见（含跨租户、私有、版权下架）
     * 返回 40403，可见但非本人返回 40301。</p>
     *
     * @param songId 歌曲 ID
     * @param userId 申诉人用户 ID
     * @return 歌曲
     */
    private Song requireAppealableSong(Long songId, Long userId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.SONG_NOT_FOUND));
        if (!userId.equals(song.getUploaderId())) {
            if (!song.visibleTo(userId, false)) {
                throw MusicErrorCode.exception(MusicErrorCode.SONG_NOT_FOUND);
            }
            throw MusicErrorCode.exception(MusicErrorCode.SONG_NOT_OWNED);
        }
        if (song.getCopyrightState() != CopyrightState.TAKEDOWN) {
            throw MusicErrorCode.exception(MusicErrorCode.APPEAL_NOT_ALLOWED);
        }
        return song;
    }

    /**
     * 加载申诉，不存在时抛出业务异常。
     *
     * @param id 申诉 ID
     * @return 申诉
     */
    private SongAppeal require(Long id) {
        return songAppealRepository.findById(id)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.APPEAL_NOT_FOUND));
    }

    /**
     * 读取审核意见（命令为空时视为未填写）。
     *
     * @param command 复核命令，可为空
     * @return 审核意见
     */
    private String opinionOf(AppealReviewCommand command) {
        return command == null ? null : command.getOpinion();
    }

    /**
     * 解析状态过滤参数：空或非法值表示不过滤。
     *
     * @param status 状态字符串
     * @return 申诉状态或 {@code null}
     */
    private AppealStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AppealStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 失效当前租户的首页缓存（版权恢复后调用）。
     */
    private void evictHomeCache() {
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            homeCacheRepository.evict(tenantId);
        }
    }

    /**
     * 申诉领域模型转视图。
     *
     * @param appeal 申诉
     * @return 申诉视图
     */
    private SongAppealView toView(SongAppeal appeal) {
        SongAppealView view = new SongAppealView();
        BeanUtils.copyProperties(appeal, view);
        view.setStatus(appeal.getStatus().name());
        return view;
    }

    /**
     * 限制页码。
     *
     * @param current 请求页码
     * @return 归一化页码
     */
    private long clampCurrent(long current) {
        return Math.max(current, 1L);
    }

    /**
     * 限制分页大小。
     *
     * @param size 请求条数
     * @return 归一化条数
     */
    private long clampSize(long size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }
}
