package com.bluewhale.video.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 电影领域模型单元测试（M4-1）：元数据校验、授权校验与到期判定。
 *
 * @author BlueWhale
 */
class MovieTest {

    /** 当前时间锚点 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 10, 0);

    /**
     * 元数据写入时去空白，空文本归一化为 {@code null}。
     */
    @Test
    void shouldNormalizeBlankMeta() {
        Movie movie = new Movie();
        movie.applyMeta("  海上钢琴师  ", "  ", " 蒂姆·罗斯 ", null, " 意大利语 ", null, BigDecimal.ONE);
        assertEquals("海上钢琴师", movie.getOriginalTitle());
        assertNull(movie.getDirector());
        assertEquals("蒂姆·罗斯", movie.getActors());
        assertNull(movie.getRegion());
        assertEquals("意大利语", movie.getLanguage());
        assertEquals(BigDecimal.ONE, movie.getRating());
    }

    /**
     * 文字字段超过上限时拒绝写入（40011）。
     */
    @Test
    void shouldRejectTooLongMeta() {
        StringBuilder longText = new StringBuilder();
        for (int index = 0; index < Movie.TEXT_MAX_LENGTH + 1; index++) {
            longText.append('长');
        }
        Movie movie = new Movie();
        assertEquals(VideoErrorCode.MOVIE_LICENSE_INVALID,
                assertThrows(BizException.class,
                        () -> movie.applyMeta(null, longText.toString(), null, null, null, null, null)).getCode());
    }

    /**
     * 评分超出 0–10 区间时拒绝写入（40011）。
     */
    @Test
    void shouldRejectRatingOutOfRange() {
        Movie movie = new Movie();
        assertEquals(VideoErrorCode.MOVIE_LICENSE_INVALID,
                assertThrows(BizException.class,
                        () -> movie.applyMeta(null, null, null, null, null, null,
                                new BigDecimal("10.5"))).getCode());
        assertEquals(VideoErrorCode.MOVIE_LICENSE_INVALID,
                assertThrows(BizException.class,
                        () -> movie.applyMeta(null, null, null, null, null, null,
                                new BigDecimal("-0.1"))).getCode());
    }

    /**
     * 合法授权写入：来源大小写不敏感、编号去空白。
     */
    @Test
    void shouldApplyValidLicense() {
        Movie movie = new Movie();
        movie.applyLicense(" licensed ", "  L-2026-001  ", NOW.plusDays(30), NOW);
        assertEquals(MovieLicenseSource.LICENSED, movie.getLicenseSource());
        assertEquals("L-2026-001", movie.getLicenseNo());
        assertEquals(NOW.plusDays(30), movie.getLicenseExpireAt());
    }

    /**
     * 授权来源非法或到期时间缺失时拒绝写入（40011）。
     */
    @Test
    void shouldRejectInvalidLicenseSourceOrExpire() {
        Movie movie = new Movie();
        assertEquals(VideoErrorCode.MOVIE_LICENSE_INVALID,
                assertThrows(BizException.class,
                        () -> movie.applyLicense("UNKNOWN", null, NOW.plusDays(1), NOW)).getCode());
        assertEquals(VideoErrorCode.MOVIE_LICENSE_INVALID,
                assertThrows(BizException.class,
                        () -> movie.applyLicense("OWNED", null, null, NOW)).getCode());
    }

    /**
     * 已过期的授权不可写入（40011）。
     */
    @Test
    void shouldRejectExpiredLicense() {
        Movie movie = new Movie();
        assertEquals(VideoErrorCode.MOVIE_LICENSE_INVALID,
                assertThrows(BizException.class,
                        () -> movie.applyLicense("OWNED", null, NOW.minusMinutes(1), NOW)).getCode());
    }

    /**
     * 授权编号超过 128 个字符时拒绝写入（40011）。
     */
    @Test
    void shouldRejectTooLongLicenseNo() {
        StringBuilder longNo = new StringBuilder();
        for (int index = 0; index < 129; index++) {
            longNo.append('A');
        }
        Movie movie = new Movie();
        assertEquals(VideoErrorCode.MOVIE_LICENSE_INVALID,
                assertThrows(BizException.class,
                        () -> movie.applyLicense("LICENSED", longNo.toString(), NOW.plusDays(1), NOW)).getCode());
    }

    /**
     * 授权仍在有效期内时允许发布。
     */
    @Test
    void shouldAssertLicenseValid() {
        Movie movie = new Movie();
        movie.applyLicense("OWNED", null, NOW.plusDays(1), NOW);
        movie.assertLicenseValid(NOW);
    }

    /**
     * 未声明授权或已到期时拒绝发布（40011）。
     */
    @Test
    void shouldRejectPublishWithoutValidLicense() {
        Movie movie = new Movie();
        assertEquals(VideoErrorCode.MOVIE_LICENSE_INVALID,
                assertThrows(BizException.class, () -> movie.assertLicenseValid(NOW)).getCode());
        movie.setLicenseSource(MovieLicenseSource.LICENSED);
        movie.setLicenseExpireAt(NOW);
        assertEquals(VideoErrorCode.MOVIE_LICENSE_INVALID,
                assertThrows(BizException.class, () -> movie.assertLicenseValid(NOW)).getCode());
    }

    /**
     * 到期判定：缺失到期时间视为已到期，精确等于当前时间视为到期。
     */
    @Test
    void shouldReportExpiredLicense() {
        Movie movie = new Movie();
        assertTrue(movie.licenseExpired(NOW));
        movie.setLicenseExpireAt(NOW);
        assertTrue(movie.licenseExpired(NOW));
        movie.setLicenseExpireAt(NOW.plusMinutes(1));
        assertFalse(movie.licenseExpired(NOW));
    }

    /**
     * 到期提醒窗口：缺失到期时间不提醒，窗口内与已到期均提醒。
     */
    @Test
    void shouldReportExpiringWithinWindow() {
        Movie movie = new Movie();
        assertFalse(movie.licenseExpiringWithin(NOW, 7));
        movie.setLicenseExpireAt(NOW.plusDays(8));
        assertFalse(movie.licenseExpiringWithin(NOW, 7));
        movie.setLicenseExpireAt(NOW.plusDays(7));
        assertTrue(movie.licenseExpiringWithin(NOW, 7));
        movie.setLicenseExpireAt(NOW.minusDays(1));
        assertTrue(movie.licenseExpiringWithin(NOW, 7));
    }
}
