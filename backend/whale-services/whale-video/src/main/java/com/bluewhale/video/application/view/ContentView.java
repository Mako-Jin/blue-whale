package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 运营侧内容视图：含内部文件引用与审核所需字段。
 *
 * <p>仅运营接口返回，需权限码保护；用户侧接口一律返回
 * {@link PublicContentView}，避免泄露 {@code fileId}。</p>
 *
 * @author BlueWhale
 */
@Data
public class ContentView implements Serializable {

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

    /** 视频文件 ID（内部引用） */
    private Long videoFileId;

    /** 封面文件 ID（内部引用） */
    private Long coverFileId;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 视频宽度（像素） */
    private Integer width;

    /** 视频高度（像素） */
    private Integer height;

    /** 文件字节数 */
    private Long sizeBytes;

    /** 上传者 ID */
    private Long uploaderId;

    /** 创作者 ID */
    private Long creatorId;

    /** 分类 ID */
    private Long categoryId;

    /** 系列 ID */
    private Long seriesId;

    /** 集数 */
    private Integer episodeNo;

    /** 可见性 */
    private String visibility;

    /** 发布状态 */
    private String publishState;

    /** 版权状态 */
    private String copyrightState;

    /** 内容来源 */
    private String source;

    /** 版权到期时间 */
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

    /** 评论数 */
    private Long commentCount;

    /** 标签 ID 列表 */
    private List<Long> tagIds;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
