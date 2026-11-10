package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 电影片单视图（公开字段，不暴露封面文件 ID）。
 *
 * @author BlueWhale
 */
@Data
public class MovieCollectionView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 片单 ID */
    private Long id;

    /** 片单标题 */
    private String title;

    /** 片单简介 */
    private String description;

    /** 封面代理地址（无封面时为 {@code null}） */
    private String coverUrl;

    /** 发布状态 */
    private String publishState;

    /** 条目数 */
    private Integer itemCount;

    /** 运营排序 */
    private Integer sortNo;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
