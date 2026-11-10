package com.bluewhale.video.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 电影运营侧视图：在内容运营视图基础上补充授权信息与海报 / 背景图文件 ID。
 *
 * <p>仅运营接口返回，需权限码保护；用户侧接口一律返回 {@link MovieView}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MovieAdminView extends ContentView {

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

    /** 授权来源 */
    private String licenseSource;

    /** 授权编号 */
    private String licenseNo;

    /** 授权到期时间 */
    private LocalDateTime licenseExpireAt;

    /** 海报文件 ID（内部引用） */
    private Long posterFileId;

    /** 背景图文件 ID（内部引用） */
    private Long backdropFileId;

    /** 海报代理地址 */
    private String posterUrl;

    /** 背景图代理地址 */
    private String backdropUrl;
}
