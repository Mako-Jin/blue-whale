package com.bluewhale.video.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 电影入库 / 编辑命令（仅运营可调用）。
 *
 * <p>入库时视频文件与时长必填；授权来源与授权到期时间必填，到期后由定时任务自动下线。
 * 海报写入内容封面列，供内容封面代理复用。</p>
 *
 * @author BlueWhale
 */
@Data
public class MovieSaveCommand {

    /** 视频文件 ID（whale-file） */
    private Long videoFileId;

    /** 标题 */
    @NotBlank(message = "{validation.video.movie.title-required}")
    @Size(max = 200, message = "{validation.video.movie.title-size}")
    private String title;

    /** 副标题 */
    @Size(max = 200, message = "{validation.video.movie.subtitle-size}")
    private String subtitle;

    /** 简介 */
    @Size(max = 2000, message = "{validation.video.movie.description-size}")
    private String description;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 视频宽度（像素） */
    private Integer width;

    /** 视频高度（像素） */
    private Integer height;

    /** 分类 ID，0 表示未分类 */
    private Long categoryId;

    /** 创作者 ID，0 表示未登记 */
    private Long creatorId;

    /** 原始片名 */
    @Size(max = 200, message = "{validation.video.movie.original-title-size}")
    private String originalTitle;

    /** 导演（多人逗号分隔） */
    @Size(max = 200, message = "{validation.video.movie.director-size}")
    private String director;

    /** 主演（多人逗号分隔） */
    @Size(max = 500, message = "{validation.video.movie.actors-size}")
    private String actors;

    /** 地区 */
    @Size(max = 64, message = "{validation.video.movie.region-size}")
    private String region;

    /** 语言 */
    @Size(max = 64, message = "{validation.video.movie.language-size}")
    private String language;

    /** 上映日期 */
    private LocalDate releaseDate;

    /** 评分（0.0–10.0） */
    private BigDecimal rating;

    /** 授权来源：OWNED / LICENSED / PUBLIC_DOMAIN */
    @NotBlank(message = "{validation.video.movie.license-source-required}")
    private String licenseSource;

    /** 授权编号 */
    @Size(max = 128, message = "{validation.video.movie.license-no-size}")
    private String licenseNo;

    /** 授权到期时间 */
    @NotNull(message = "{validation.video.movie.license-expire-required}")
    private LocalDateTime licenseExpireAt;

    /** 海报文件 ID；更新时为空表示保留原海报，0 表示清除 */
    private Long posterFileId;

    /** 背景图文件 ID；更新时为空表示保留原背景图，0 表示清除 */
    private Long backdropFileId;
}
