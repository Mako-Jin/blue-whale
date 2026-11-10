package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 视频系列实体，对应表 {@code vd_series}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vd_series")
public class SeriesEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 系列标题 */
    private String title;

    /** 系列简介 */
    private String description;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 内容类型：当前仅 VIDEO */
    private String contentType;

    /** 分类 ID */
    private Long categoryId;

    /** 创作者 ID */
    private Long creatorId;

    /** 创建人用户 ID */
    private Long uploaderId;

    /** 发布状态 */
    private String publishStatus;

    /** 已发布集数 */
    private Integer episodeCount;

    /** 运营排序 */
    private Integer sortNo;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;
}
