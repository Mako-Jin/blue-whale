package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户侧内容视图（列表 / 详情基础字段）。
 *
 * <p>不包含任何文件 ID：封面统一由 {@code /video/content/{id}/cover} 代理，
 * 播放统一使用播放票据 + {@code /video/stream/{id}}。</p>
 *
 * @author BlueWhale
 */
@Data
public class PublicContentView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内容 ID */
    private Long id;

    /** 内容类型 */
    private String contentType;

    /** 标题 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 简介 */
    private String description;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 视频宽度 */
    private Integer width;

    /** 视频高度 */
    private Integer height;

    /** 视频格式 */
    private String format;

    /** 分类 ID */
    private Long categoryId;

    /** 创作者 ID */
    private Long creatorId;

    /** 创作者昵称 */
    private String creatorName;

    /** 系列 ID */
    private Long seriesId;

    /** 集数 */
    private Integer episodeNo;

    /** 可见性 */
    private String visibility;

    /** 播放次数 */
    private Long playCount;

    /** 点赞数 */
    private Long likeCount;

    /** 收藏数 */
    private Long favoriteCount;

    /** 评论数 */
    private Long commentCount;

    /** 标签名称列表 */
    private List<String> tags;

    /** 封面代理地址（无封面时为 {@code null}） */
    private String coverUrl;

    /** 是否可播放 */
    private boolean playable;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;
}
