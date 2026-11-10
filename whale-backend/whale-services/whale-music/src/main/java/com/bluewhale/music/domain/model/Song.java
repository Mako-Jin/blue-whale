package com.bluewhale.music.domain.model;

import com.bluewhale.music.domain.exception.MusicErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌曲领域模型（聚合根）。
 *
 * <p>状态流转：草稿（DRAFT）→ 上架（PUBLISHED）→ 下架（OFFLINE）；
 * 版权下架（TAKEDOWN）优先级最高，强制切到 OFFLINE 并禁止播放。
 * 可见性：PUBLIC 租户内可见；PRIVATE 仅上传者本人可见，运营可通过管理端查看。</p>
 *
 * @author BlueWhale
 */
@Data
public class Song {

    /** 主键 */
    private Long id;

    /** 上传者用户 ID */
    private Long uploaderId;

    /** 可见性 */
    private SongVisibility visibility;

    /** 专辑 ID，0 表示单曲 */
    private Long albumId;

    /** 歌手 ID */
    private Long artistId;

    /** 歌曲标题 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 音频文件 ID（whale-file） */
    private Long audioFileId;

    /** 封面文件 ID（whale-file） */
    private Long coverFileId;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 歌词文件 ID（whale-file） */
    private Long lyricFileId;

    /** 歌词纯文本 */
    private String lyricText;

    /** 语言：ZH / EN / JA / OTHER */
    private String language;

    /** 发布状态 */
    private PublishState publishState;

    /** 版权状态 */
    private CopyrightState copyrightState;

    /** 作品上传来源（版权合规留痕，M5-3） */
    private CopyrightSource source;

    /** 播放次数 */
    private Long playCount;

    /** 收藏数 */
    private Long likeCount;

    /** 评论数 */
    private Long commentCount;

    /** 排序权重 */
    private Integer sortWeight;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;

    /** 版权下架原因 */
    private String takedownReason;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 上架歌曲：校验发布前置条件并切换到 PUBLISHED。
     *
     * <p>前置条件：标题有效、歌手与音频存在、时长大于 0、版权未下架。
     * 已上架时重复调用幂等（仅刷新上架时间）。</p>
     */
    public void publish() {
        if (copyrightState == CopyrightState.TAKEDOWN) {
            throw MusicErrorCode.exception(MusicErrorCode.SONG_STATE_CONFLICT);
        }
        if (title == null || title.isBlank() || title.length() > 120) {
            throw MusicErrorCode.exception(MusicErrorCode.SONG_TITLE_INVALID);
        }
        if (artistId == null || artistId <= 0 || audioFileId == null || audioFileId <= 0
                || durationMs == null || durationMs <= 0) {
            throw MusicErrorCode.exception(MusicErrorCode.SONG_NOT_READY);
        }
        publishState = PublishState.PUBLISHED;
        publishedAt = LocalDateTime.now();
    }

    /**
     * 下架歌曲：草稿不可下架，重复下架幂等。
     */
    public void offline() {
        if (publishState == PublishState.DRAFT) {
            throw MusicErrorCode.exception(MusicErrorCode.SONG_STATE_CONFLICT);
        }
        publishState = PublishState.OFFLINE;
    }

    /**
     * 版权下架：记录原因并强制切到 OFFLINE 与 TAKEDOWN。
     *
     * @param reason 下架原因（不可为空）
     */
    public void takedown(String reason) {
        if (reason == null || reason.isBlank()) {
            throw MusicErrorCode.exception(MusicErrorCode.TAKEDOWN_REASON_REQUIRED);
        }
        copyrightState = CopyrightState.TAKEDOWN;
        publishState = PublishState.OFFLINE;
        takedownReason = reason.trim();
    }

    /**
     * 申诉通过后恢复版权：仅版权下架状态可恢复。
     *
     * <p>恢复后版权状态置为 {@link CopyrightState#VALID}，发布状态保留为
     * {@link PublishState#OFFLINE}，需运营显式重新上架后才可播放，
     * 避免申诉通过即自动复播带来的二次风险。</p>
     */
    public void restoreCopyright() {
        if (copyrightState != CopyrightState.TAKEDOWN) {
            throw MusicErrorCode.exception(MusicErrorCode.APPEAL_NOT_ALLOWED);
        }
        copyrightState = CopyrightState.VALID;
        publishState = PublishState.OFFLINE;
        takedownReason = null;
    }

    /**
     * 判断歌曲当前是否可播放。
     *
     * @return 已上架且版权未下架返回 {@code true}
     */
    public boolean playable() {
        return publishState == PublishState.PUBLISHED
                && copyrightState != null
                && copyrightState.canPlay();
    }

    /**
     * 判断指定用户是否可见该歌曲。
     *
     * @param viewerId 查看者用户 ID，可为 {@code null}
     * @param operator 是否为曲库运营人员（可见全部并含草稿）
     * @return 可见返回 {@code true}
     */
    public boolean visibleTo(Long viewerId, boolean operator) {
        if (operator) {
            return true;
        }
        if (publishState != PublishState.PUBLISHED || copyrightState == CopyrightState.TAKEDOWN) {
            return false;
        }
        return visibility == SongVisibility.PUBLIC || (viewerId != null && viewerId.equals(uploaderId));
    }

    /**
     * 校验歌曲归属，非本人操作直接拒绝。
     *
     * @param userId 操作用户 ID
     */
    public void assertOwnedBy(Long userId) {
        if (userId == null || !userId.equals(uploaderId)) {
            throw MusicErrorCode.exception(MusicErrorCode.SONG_NOT_OWNED);
        }
    }

    /**
     * 声明作品上传来源，空值保留原值（更新场景）。
     *
     * @param value 来源文本（ORIGINAL / AUTHORIZED / ROYALTY_FREE）
     */
    public void applySource(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        source = CopyrightSource.parse(value);
    }
}
