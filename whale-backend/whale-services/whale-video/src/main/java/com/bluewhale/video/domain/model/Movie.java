package com.bluewhale.video.domain.model;

import com.bluewhale.video.domain.exception.VideoErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 电影元数据聚合（以内容 ID 为标识，1:1 扩展 {@code vd_content}）。
 *
 * <p>仅运营可入库与发布；授权来源、授权编号与到期时间必须可追溯，
 * 授权到期后由定时任务自动下线，且内容聚合（{@code copyrightExpireAt}）
 * 同步阻断播放。公开视图不暴露海报 / 背景图的文件 ID。</p>
 *
 * @author BlueWhale
 */
@Data
public class Movie {

    /** 评分下限 */
    public static final BigDecimal MIN_RATING = BigDecimal.ZERO;

    /** 评分上限 */
    public static final BigDecimal MAX_RATING = BigDecimal.TEN;

    /** 文字字段最大长度（导演 / 演员 / 地区 / 语言） */
    public static final int TEXT_MAX_LENGTH = 500;

    /** 内容 ID（主键，同时是 vd_content.id） */
    private Long contentId;

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
    private MovieLicenseSource licenseSource;

    /** 授权编号 */
    private String licenseNo;

    /** 授权到期时间 */
    private LocalDateTime licenseExpireAt;

    /** 海报文件 ID（whale-file，公开 VO 不暴露） */
    private Long posterFileId;

    /** 背景图文件 ID（whale-file，公开 VO 不暴露） */
    private Long backdropFileId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 写入电影元数据并校验长度与评分范围。
     *
     * @param originalTitle 原始片名，可为空
     * @param director 导演，可为空
     * @param actors 主演，可为空
     * @param region 地区，可为空
     * @param language 语言，可为空
     * @param releaseDate 上映日期，可为空
     * @param rating 评分，可为空
     */
    public void applyMeta(String originalTitle, String director, String actors, String region,
                          String language, LocalDate releaseDate, BigDecimal rating) {
        this.originalTitle = normalize(originalTitle);
        this.director = normalize(director);
        this.actors = normalize(actors);
        this.region = normalize(region);
        this.language = normalize(language);
        this.releaseDate = releaseDate;
        if (rating != null && (rating.compareTo(MIN_RATING) < 0 || rating.compareTo(MAX_RATING) > 0)) {
            throw VideoErrorCode.exception(VideoErrorCode.MOVIE_LICENSE_INVALID);
        }
        this.rating = rating;
    }

    /**
     * 写入并校验授权信息。
     *
     * <p>授权来源必须合法；授权到期时间必填，且不得早于当前时间
     * （已过期的授权无法用于发布）。</p>
     *
     * @param source 授权来源文本
     * @param licenseNo 授权编号，可由调用方为空
     * @param expireAt 授权到期时间
     * @param now 当前时间
     */
    public void applyLicense(String source, String licenseNo, LocalDateTime expireAt, LocalDateTime now) {
        MovieLicenseSource parsed = MovieLicenseSource.parse(source);
        if (parsed == null || expireAt == null) {
            throw VideoErrorCode.exception(VideoErrorCode.MOVIE_LICENSE_INVALID);
        }
        if (expireAt.isBefore(now)) {
            throw VideoErrorCode.exception(VideoErrorCode.MOVIE_LICENSE_INVALID);
        }
        String trimmedNo = licenseNo == null ? null : licenseNo.trim();
        if (trimmedNo != null && trimmedNo.length() > 128) {
            throw VideoErrorCode.exception(VideoErrorCode.MOVIE_LICENSE_INVALID);
        }
        this.licenseSource = parsed;
        this.licenseNo = trimmedNo;
        this.licenseExpireAt = expireAt;
    }

    /**
     * 校验授权在指定时间仍有效，否则拒绝发布。
     *
     * @param now 当前时间
     */
    public void assertLicenseValid(LocalDateTime now) {
        if (licenseSource == null || licenseExpireAt == null || !licenseExpireAt.isAfter(now)) {
            throw VideoErrorCode.exception(VideoErrorCode.MOVIE_LICENSE_INVALID);
        }
    }

    /**
     * 判断授权是否在指定时间点已到期。
     *
     * @param now 当前时间
     * @return 已到期返回 {@code true}
     */
    public boolean licenseExpired(LocalDateTime now) {
        return licenseExpireAt == null || !licenseExpireAt.isAfter(now);
    }

    /**
     * 判断授权是否将在指定天数内到期（用于运营提醒）。
     *
     * @param now 当前时间
     * @param days 提醒窗口天数
     * @return 在窗口内（含已到期）返回 {@code true}
     */
    public boolean licenseExpiringWithin(LocalDateTime now, int days) {
        if (licenseExpireAt == null) {
            return false;
        }
        return !licenseExpireAt.isAfter(now.plusDays(Math.max(days, 0)));
    }

    /**
     * 归一化并校验文字字段长度。
     *
     * @param value 原始文本
     * @return 去空白后的文本；空值返回 {@code null}
     */
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > TEXT_MAX_LENGTH) {
            throw VideoErrorCode.exception(VideoErrorCode.MOVIE_LICENSE_INVALID);
        }
        return trimmed;
    }
}
