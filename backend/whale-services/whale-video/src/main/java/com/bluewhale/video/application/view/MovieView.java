package com.bluewhale.video.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 电影用户侧视图：在内容列表视图基础上补充电影元数据与图片代理地址。
 *
 * <p>不包含任何文件 ID 与授权细节；海报 / 背景图统一由
 * {@code /video/movies/{id}/poster} 与 {@code /video/movies/{id}/backdrop} 代理。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MovieView extends PublicContentView {

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

    /** 海报代理地址（无海报时为 {@code null}） */
    private String posterUrl;

    /** 背景图代理地址（无背景图时为 {@code null}） */
    private String backdropUrl;
}
