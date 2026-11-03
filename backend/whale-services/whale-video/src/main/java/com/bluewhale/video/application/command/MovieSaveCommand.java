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
    @NotBlank(message = "电影标题不能为空")
    @Size(max = 200, message = "电影标题不能超过 200 个字符")
    private String title;

    /** 副标题 */
    @Size(max = 200, message = "副标题不能超过 200 个字符")
    private String subtitle;

    /** 简介 */
    @Size(max = 2000, message = "简介不能超过 2000 个字符")
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
    @Size(max = 200, message = "原始片名不能超过 200 个字符")
    private String originalTitle;

    /** 导演（多人逗号分隔） */
    @Size(max = 200, message = "导演不能超过 200 个字符")
    private String director;

    /** 主演（多人逗号分隔） */
    @Size(max = 500, message = "主演不能超过 500 个字符")
    private String actors;

    /** 地区 */
    @Size(max = 64, message = "地区不能超过 64 个字符")
    private String region;

    /** 语言 */
    @Size(max = 64, message = "语言不能超过 64 个字符")
    private String language;

    /** 上映日期 */
    private LocalDate releaseDate;

    /** 评分（0.0–10.0） */
    private BigDecimal rating;

    /** 授权来源：OWNED / LICENSED / PUBLIC_DOMAIN */
    @NotBlank(message = "必须声明电影授权来源")
    private String licenseSource;

    /** 授权编号 */
    @Size(max = 128, message = "授权编号不能超过 128 个字符")
    private String licenseNo;

    /** 授权到期时间 */
    @NotNull(message = "必须填写电影授权到期时间")
    private LocalDateTime licenseExpireAt;

    /** 海报文件 ID；更新时为空表示保留原海报，0 表示清除 */
    private Long posterFileId;

    /** 背景图文件 ID；更新时为空表示保留原背景图，0 表示清除 */
    private Long backdropFileId;
}
