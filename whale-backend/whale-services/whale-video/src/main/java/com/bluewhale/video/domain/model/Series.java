package com.bluewhale.video.domain.model;

import com.bluewhale.video.domain.exception.VideoErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频系列聚合根：运营维护的视频合集，集数由内容聚合关联。
 *
 * <p>状态流转：草稿（DRAFT）→ 上架（PUBLISHED）→ 下架（OFFLINE）；
 * 重复上架幂等并刷新上架时间，草稿不可直接下架。公开系列页只展示
 * 已上架系列与已发布集数，草稿与已下架系列对用户侧按不存在处理。</p>
 *
 * <p>系列封面由 {@code whale-file} 托管，本聚合只保存 {@code coverFileId}，
 * 公开视图统一返回 {@code /video/series/{id}/cover} 代理地址。</p>
 *
 * @author BlueWhale
 */
@Data
public class Series {

    /** 标题最大长度 */
    public static final int TITLE_MAX_LENGTH = 200;

    /** 主键 */
    private Long id;

    /** 系列标题 */
    private String title;

    /** 系列简介 */
    private String description;

    /** 封面文件 ID（whale-file） */
    private Long coverFileId;

    /** 内容类型（当前仅 VIDEO 可入系列） */
    private ContentType contentType;

    /** 分类 ID，0 表示未分类 */
    private Long categoryId;

    /** 创作者 ID，0 表示未登记 */
    private Long creatorId;

    /** 创建人用户 ID（运营） */
    private Long uploaderId;

    /** 发布状态 */
    private PublishState publishState;

    /** 已发布集数 */
    private Integer episodeCount;

    /** 运营排序（升序） */
    private Integer sortNo;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 设置并校验系列标题。
     *
     * @param value 标题文本
     */
    public void retitle(String value) {
        if (value == null || value.isBlank() || value.trim().length() > TITLE_MAX_LENGTH) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_TITLE_INVALID);
        }
        title = value.trim();
    }

    /**
     * 上架系列：已上架时幂等（仅刷新上架时间）。
     */
    public void publish() {
        if (publishState == PublishState.PUBLISHED) {
            publishedAt = LocalDateTime.now();
            return;
        }
        if (title == null || title.isBlank() || title.length() > TITLE_MAX_LENGTH) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_TITLE_INVALID);
        }
        publishState = PublishState.PUBLISHED;
        publishedAt = LocalDateTime.now();
    }

    /**
     * 下架系列：草稿不可下架，重复下架幂等。
     */
    public void offline() {
        if (publishState == PublishState.DRAFT || publishState == PublishState.PENDING_REVIEW) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_STATE_CONFLICT);
        }
        publishState = PublishState.OFFLINE;
    }

    /**
     * 判断系列是否对用户侧可见。
     *
     * @return 已上架返回 {@code true}
     */
    public boolean published() {
        return publishState == PublishState.PUBLISHED;
    }

    /**
     * 刷新已发布集数（不小于 0）。
     *
     * @param count 已发布集数
     */
    public void refreshEpisodeCount(int count) {
        episodeCount = Math.max(count, 0);
    }
}
