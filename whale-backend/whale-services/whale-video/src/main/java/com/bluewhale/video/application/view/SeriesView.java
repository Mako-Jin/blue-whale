package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系列视图（公开字段，不暴露文件 ID）。
 *
 * <p>封面统一由 {@code /video/series/{id}/cover} 代理，用户侧仅已上架系列可见。</p>
 *
 * @author BlueWhale
 */
@Data
public class SeriesView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 系列 ID */
    private Long id;

    /** 系列标题 */
    private String title;

    /** 系列简介 */
    private String description;

    /** 内容类型 */
    private String contentType;

    /** 分类 ID */
    private Long categoryId;

    /** 创作者 ID */
    private Long creatorId;

    /** 创作者昵称 */
    private String creatorName;

    /** 发布状态 */
    private String publishState;

    /** 已发布集数 */
    private Integer episodeCount;

    /** 运营排序 */
    private Integer sortNo;

    /** 封面代理地址（无封面时为 {@code null}） */
    private String coverUrl;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
