package com.bluewhale.video.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 电影详情视图：在内容详情视图基础上补充电影元数据、图片代理与所属片单。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MovieDetailView extends ContentDetailView {

    private static final long serialVersionUID = 1L;

    /** 原始片名 */
    private String originalTitle;

    /** 导演（多人逗号分隔） */
    private String director;

    /** 主演（多人逗号分隔） */
    private String actors;

    /** 地区 */
    private String region;

    /** 语言 */
    private String language;

    /** 上映日期 */
    private LocalDate releaseDate;

    /** 评分（0.0–10.0） */
    private BigDecimal rating;

    /** 海报代理地址 */
    private String posterUrl;

    /** 背景图代理地址 */
    private String backdropUrl;

    /** 收录本片的已上架片单 */
    private List<MovieCollectionView> collections;
}
