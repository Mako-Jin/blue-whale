package com.bluewhale.map.overview;

import com.bluewhale.map.entity.MapQuotaUsage;
import com.bluewhale.map.mapper.MapFenceMapper;
import com.bluewhale.map.mapper.MapGeocodeCacheMapper;
import com.bluewhale.map.mapper.MapLocationMapper;
import com.bluewhale.map.mapper.MapQuotaUsageMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MapOverviewStatsService} 单元测试：租户闸口、各家 count 的取数顺序、
 * 配额按日累加（同一日多供应商相加、0 表示不限）与七日窗口。
 *
 * <p>{@code selectCount} 的入参是包装器对象，按调用顺序回放比断言 SQL 片段稳；
 * 代价是加一次查询就要同步改这里。这是刻意接受的：口径算错会让屏风上的数与
 * 台账对不上，比测试脆弱更难查。</p>
 *
 * @author BlueWhale
 */
class MapOverviewStatsServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 10, 30);

    private final MapLocationMapper locationMapper = mock(MapLocationMapper.class);

    private final MapFenceMapper fenceMapper = mock(MapFenceMapper.class);

    private final MapGeocodeCacheMapper cacheMapper = mock(MapGeocodeCacheMapper.class);

    private final MapQuotaUsageMapper quotaMapper = mock(MapQuotaUsageMapper.class);

    private final MapOverviewStatsService service =
            new MapOverviewStatsService(locationMapper, fenceMapper, cacheMapper, quotaMapper);

    /**
     * 按调用顺序喂各家计数。
     *
     * @param locations 打点总数
     * @param fences    围栏总数
     * @param enabled   启用围栏
     * @param circle    圆形围栏
     * @param polygon   多边形围栏
     * @param cache     缓存总数
     * @param expired   过期缓存
     */
    private void stubCounts(long locations, long fences, long enabled, long circle, long polygon,
            long cache, long expired) {
        when(locationMapper.selectCount(any())).thenReturn(locations);
        when(fenceMapper.selectCount(any())).thenReturn(fences, enabled, circle, polygon);
        when(cacheMapper.selectCount(any())).thenReturn(cache, expired);
    }

    /**
     * 造一行配额台账。
     *
     * @param provider 供应商
     * @param day      日期
     * @param used     已用
     * @param limit    上限（0 表示不限）
     * @return 台账行
     */
    private static MapQuotaUsage usage(String provider, LocalDate day, Integer used, Integer limit) {
        MapQuotaUsage row = new MapQuotaUsage();
        row.setProvider(provider);
        row.setUsageDate(day);
        row.setUsedCount(used);
        row.setLimitCount(limit);
        return row;
    }

    @Test
    @DisplayName("缺租户直接抛：宁可让屏风降级，也不显示跨租户的和")
    void shouldRejectMissingTenant() {
        assertThatThrownBy(() -> service.snapshot(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("各家计数原样进快照，停用围栏由总数相减得出")
    void shouldCarryCountsThrough() {
        stubCounts(120L, 6L, 4L, 3L, 3L, 80L, 2L);
        when(quotaMapper.selectList(any())).thenReturn(List.of());

        MapOverviewStats stats = service.snapshot(9001L, NOW);

        assertThat(stats.locationTotal()).isEqualTo(120L);
        assertThat(stats.fenceTotal()).isEqualTo(6L);
        assertThat(stats.fenceEnabled()).isEqualTo(4L);
        assertThat(stats.fenceDisabled()).isEqualTo(2L);
        assertThat(stats.fenceCircle()).isEqualTo(3L);
        assertThat(stats.fencePolygon()).isEqualTo(3L);
        assertThat(stats.cacheTotal()).isEqualTo(80L);
        assertThat(stats.cacheExpired()).isEqualTo(2L);
    }

    @Test
    @DisplayName("同一日多供应商相加；上限为 0 的不参与合计，也不稀释别家")
    void shouldSumQuotaAcrossProviders() {
        stubCounts(0L, 0L, 0L, 0L, 0L, 0L, 0L);
        LocalDate today = NOW.toLocalDate();
        when(quotaMapper.selectList(any())).thenReturn(List.of(
                usage("tencent", today, 300, 1000),
                usage("amap", today, 120, 0)));

        MapOverviewStats stats = service.snapshot(9001L, NOW);

        assertThat(stats.quotaUsedToday()).isEqualTo(420L);
        assertThat(stats.quotaLimitToday()).isEqualTo(1000L);
        assertThat(stats.providersToday()).isEqualTo(2);
    }

    @Test
    @DisplayName("近七日按日合计，升序排列且今天在最后")
    void shouldFillDailySeries() {
        stubCounts(0L, 0L, 0L, 0L, 0L, 0L, 0L);
        LocalDate today = NOW.toLocalDate();
        when(quotaMapper.selectList(any())).thenReturn(List.of(
                usage("tencent", today, 30, 1000),
                usage("amap", today, 20, 0),
                usage("tencent", today.minusDays(3), 7, 1000),
                usage("tencent", today.minusDays(6), 5, 1000)));

        MapOverviewStats stats = service.snapshot(9001L, NOW);

        assertThat(stats.recentDays()).hasSize(MapOverviewStatsService.TREND_WINDOW_DAYS);
        assertThat(stats.recentDays().get(6).day()).isEqualTo(today);
        assertThat(stats.recentDays().get(6).used()).isEqualTo(50L);
        assertThat(stats.recentDays().get(3).used()).isEqualTo(7L);
        assertThat(stats.recentDays().get(0).used()).isEqualTo(5L);
        assertThat(stats.recentDays().get(1).used()).isZero();
    }

    @Test
    @DisplayName("窗口外的行不进柱图：即使 mapper 多给了也不能算进去")
    void shouldIgnoreRowsOutsideWindow() {
        stubCounts(0L, 0L, 0L, 0L, 0L, 0L, 0L);
        LocalDate today = NOW.toLocalDate();
        when(quotaMapper.selectList(any())).thenReturn(List.of(
                usage("tencent", today.minusDays(9), 900, 1000)));

        MapOverviewStats stats = service.snapshot(9001L, NOW);

        assertThat(stats.recentDays()).allSatisfy(day -> assertThat(day.used()).isZero());
        assertThat(stats.quotaUsedToday()).isZero();
    }

    @Test
    @DisplayName("selectCount 给 null、用量字段为 null 都按 0 处理")
    void shouldTreatNullsAsZero() {
        when(locationMapper.selectCount(any())).thenReturn(null);
        when(fenceMapper.selectCount(any())).thenReturn(null);
        when(cacheMapper.selectCount(any())).thenReturn(null);
        when(quotaMapper.selectList(any())).thenReturn(List.of(
                usage("tencent", NOW.toLocalDate(), null, null)));

        MapOverviewStats stats = service.snapshot(9001L, NOW);

        assertThat(stats.locationTotal()).isZero();
        assertThat(stats.fenceTotal()).isZero();
        assertThat(stats.cacheTotal()).isZero();
        assertThat(stats.quotaUsedToday()).isZero();
        assertThat(stats.quotaLimitToday()).isZero();
        assertThat(stats.providersToday()).isEqualTo(1);
    }

    @Test
    @DisplayName("窗口口径：七日柱图，今天算第一天")
    void shouldUseSevenDayWindow() {
        assertThat(MapOverviewStatsService.TREND_WINDOW_DAYS).isEqualTo(7);
    }
}
