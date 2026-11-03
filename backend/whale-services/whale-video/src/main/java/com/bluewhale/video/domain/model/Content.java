package com.bluewhale.video.domain.model;

import com.bluewhale.video.domain.exception.VideoErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 视频内容聚合根（短视频 / 视频 / 电影共享）。
 *
 * <p>状态流转：草稿（DRAFT）→ 待审（PENDING_REVIEW）→ 上架（PUBLISHED）→
 * 下架（OFFLINE）；版权下架（TAKEDOWN）优先级最高，强制切到 OFFLINE 并
 * 禁止签发播放票据与继续流式读取。电影授权到期后同样不可播放。</p>
 *
 * <p>视频字节统一托管在 {@code whale-file}，本聚合只保存 {@code videoFileId} /
 * {@code coverFileId} 等内部引用，公开视图不得暴露。</p>
 *
 * @author BlueWhale
 */
@Data
public class Content {

    /** 标题最大长度 */
    public static final int TITLE_MAX_LENGTH = 200;

    /** 主键 */
    private Long id;

    /** 内容类型 */
    private ContentType contentType;

    /** 标题 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 简介 */
    private String description;

    /** 封面文件 ID（whale-file） */
    private Long coverFileId;

    /** 视频文件 ID（whale-file） */
    private Long videoFileId;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 视频宽度 */
    private Integer width;

    /** 视频高度 */
    private Integer height;

    /** 视频格式：MP4 / WEBM */
    private String format;

    /** 文件字节数 */
    private Long sizeBytes;

    /** 上传者用户 ID（电影为 0） */
    private Long uploaderId;

    /** 创作者 ID，0 表示未登记 */
    private Long creatorId;

    /** 分类 ID，0 表示未分类 */
    private Long categoryId;

    /** 系列 ID，0 表示不属系列 */
    private Long seriesId;

    /** 集数，0 表示非系列内容 */
    private Integer episodeNo;

    /** 可见性 */
    private ContentVisibility visibility;

    /** 发布状态 */
    private PublishState publishState;

    /** 版权状态 */
    private CopyrightState copyrightState;

    /** 内容来源声明 */
    private CopyrightSource source;

    /** 版权 / 授权到期时间 */
    private LocalDateTime copyrightExpireAt;

    /** 版权下架原因 */
    private String takedownReason;

    /** 版权下架时间 */
    private LocalDateTime takedownAt;

    /** 播放次数 */
    private Long playCount;

    /** 点赞数 */
    private Long likeCount;

    /** 收藏数 */
    private Long favoriteCount;

    /** 评论数（仅统计可见评论） */
    private Long commentCount;

    /** 热度分 */
    private BigDecimal hotScore;

    /** 运营排序权重 */
    private Integer sortWeight;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 发布内容：校验前置条件后切到 PUBLISHED。
     *
     * <p>前置条件：标题有效、视频文件存在、时长大于 0 且不超过该类型上限、
     * 版权未下架、授权未过期。已发布时重复调用幂等（仅刷新上架时间）。</p>
     *
     * @param maxDurationMs 该内容类型的时长上限（毫秒）
     */
    public void publish(long maxDurationMs) {
        if (copyrightState == CopyrightState.TAKEDOWN) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_STATE_CONFLICT);
        }
        if (title == null || title.isBlank() || title.length() > TITLE_MAX_LENGTH) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_TITLE_INVALID);
        }
        if (videoFileId == null || videoFileId <= 0 || durationMs == null || durationMs <= 0
                || durationMs > maxDurationMs) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_DURATION_INVALID);
        }
        if (copyrightExpireAt != null && copyrightExpireAt.isBefore(LocalDateTime.now())) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_STATE_CONFLICT);
        }
        publishState = PublishState.PUBLISHED;
        publishedAt = LocalDateTime.now();
    }

    /**
     * 下架内容：草稿不可下架，重复下架幂等。
     */
    public void offline() {
        if (publishState == PublishState.DRAFT || publishState == PublishState.PENDING_REVIEW) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_STATE_CONFLICT);
        }
        publishState = PublishState.OFFLINE;
    }

    /**
     * 版权下架：记录原因与时间，并强制切到 OFFLINE 与 TAKEDOWN。
     *
     * @param reason 下架原因（不可为空）
     */
    public void takedown(String reason) {
        if (reason == null || reason.isBlank()) {
            throw VideoErrorCode.exception(VideoErrorCode.TAKEDOWN_REASON_REQUIRED);
        }
        copyrightState = CopyrightState.TAKEDOWN;
        publishState = PublishState.OFFLINE;
        takedownReason = reason.trim();
        takedownAt = LocalDateTime.now();
    }

    /**
     * 申诉通过 / 重新授权后恢复版权：仅版权下架状态可恢复。
     *
     * <p>恢复后发布状态保留为 OFFLINE，需运营显式重新上架后才可播放。</p>
     */
    public void restoreCopyright() {
        if (copyrightState != CopyrightState.TAKEDOWN) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_STATE_CONFLICT);
        }
        copyrightState = CopyrightState.AUTHORIZED;
        publishState = PublishState.OFFLINE;
        takedownReason = null;
        takedownAt = null;
    }

    /**
     * 判断内容当前是否可播放。
     *
     * @return 已发布、版权有效且授权未过期返回 {@code true}
     */
    public boolean playable() {
        if (publishState != PublishState.PUBLISHED) {
            return false;
        }
        if (copyrightState == null || !copyrightState.canPlay()) {
            return false;
        }
        return copyrightExpireAt == null || copyrightExpireAt.isAfter(LocalDateTime.now());
    }

    /**
     * 判断内容是否允许评论（私密内容不开放评论）。
     *
     * @return 可播放且公开返回 {@code true}
     */
    public boolean commentable() {
        return playable() && visibility == ContentVisibility.PUBLIC;
    }

    /**
     * 判断指定用户是否可见该内容。
     *
     * @param viewerId 查看者用户 ID，可为 {@code null}
     * @param operator 是否为视频运营人员（可见全部，含草稿）
     * @return 可见返回 {@code true}
     */
    public boolean visibleTo(Long viewerId, boolean operator) {
        if (operator) {
            return true;
        }
        if (publishState != PublishState.PUBLISHED || !copyrightState.canPlay()
                || (copyrightExpireAt != null && copyrightExpireAt.isBefore(LocalDateTime.now()))) {
            return false;
        }
        return visibility == ContentVisibility.PUBLIC || (viewerId != null && viewerId.equals(uploaderId));
    }

    /**
     * 校验内容归属，非本人操作直接拒绝。
     *
     * @param userId 操作用户 ID
     */
    public void assertOwnedBy(Long userId) {
        if (userId == null || !userId.equals(uploaderId)) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_OWNED);
        }
    }

    /**
     * 校验内容类型符合预期。
     *
     * @param expected 期望类型
     */
    public void assertType(ContentType expected) {
        if (expected == null || contentType == null || contentType != expected) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND);
        }
    }

    /**
     * 声明内容来源，空值保留原值（更新场景）。
     *
     * @param value 来源文本（ORIGINAL / AUTHORIZED / ROYALTY_FREE）
     */
    public void applySource(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        CopyrightSource parsed = CopyrightSource.parse(value);
        if (parsed == null) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_SOURCE_INVALID);
        }
        source = parsed;
    }

    /**
     * 校验内容来源是否已声明。
     */
    public void assertSourceDeclared() {
        if (source == null) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_SOURCE_REQUIRED);
        }
    }
}
