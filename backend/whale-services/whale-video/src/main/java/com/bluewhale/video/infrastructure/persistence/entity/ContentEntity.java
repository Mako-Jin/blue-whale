package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 视频共享内容实体，对应表 {@code vd_content}。
 *
 * <p>短视频 / 视频 / 电影共用；文件 ID 仅作为内部引用，公开视图不暴露。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vd_content")
public class ContentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 内容类型 */
    private String contentType;

    /** 标题 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 简介 */
    private String description;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 视频文件 ID */
    private Long videoFileId;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 视频宽度 */
    private Integer width;

    /** 视频高度 */
    private Integer height;

    /** 视频格式 */
    private String format;

    /** 文件字节数 */
    private Long sizeBytes;

    /** 上传者用户 ID */
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

    /** 发布状态，对应列 {@code publish_status} */
    @TableField("publish_status")
    private String publishState;

    /** 版权状态，对应列 {@code copyright_status} */
    @TableField("copyright_status")
    private String copyrightState;

    /** 内容来源，对应列 {@code copyright_source} */
    @TableField("copyright_source")
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

    /** 热度分 */
    private BigDecimal hotScore;

    /** 运营排序权重 */
    private Integer sortWeight;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;
}
