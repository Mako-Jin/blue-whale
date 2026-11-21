package com.bluewhale.file.overview;

import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery;
import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery.BucketCount;
import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery.DayCount;
import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery.LibraryStat;
import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery.RecycleStat;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewChart;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileOverviewProvider} 单元测试（T5.4）。
 *
 * <p>钉住五件容易悄悄漂移的事：① KPI 顺序与单位（屏风按位置排版，换序/换单位没人报错）；
 * ② 文件数与占用空间是两套口径（前者根文件、后者含版本行），混用会一边虚高一边骗人；
 * ③ 趋势缺日必须补 0；④ 桶超过 5 个要合并成「其他」且合计守恒；
 * ⑤ 回收站告警只在"清理确实开着"时才报——配置成不清理不是故障。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FileOverviewProviderTest {

    /** 测试租户 */
    private static final long TENANT_ID = 7L;

    /** 1 GB */
    private static final long GB = 1024L * 1024L * 1024L;

    /** 统计口 */
    @Mock
    private FileLibraryStatQuery statQuery;

    /** 文件服务配置（回收站保留期/开关） */
    private FileProperties properties;

    /** 被测对象 */
    private FileOverviewProvider provider;

    /**
     * 每个用例前重建 provider：配置对象是可变的，用例之间不能共享状态
     */
    @BeforeEach
    void setUp() {
        properties = new FileProperties();
        provider = new FileOverviewProvider(statQuery, properties);
    }

    /**
     * 正常形态：四张 KPI（顺序固定）、两张图、一个文件台账入口、无告警
     */
    @Test
    @DisplayName("正常：四张 KPI + 两张图 + 文件台账入口，无告警")
    void shouldBuildKpisChartsAndLink() {
        stubBaseline(new LibraryStat(128L, 3L * GB), 6L, new RecycleStat(4L, 0L, 0L),
                List.of(), List.of(new BucketCount("doc", 100L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:file:list")));

        assertEquals("file", snapshot.service());
        assertEquals("cangshu-hall", snapshot.nodeKey());
        assertEquals(List.of("files", "bytes", "today", "recycle"),
                snapshot.kpis().stream().map(OverviewKpi::key).toList());
        assertEquals(128d, kpi(snapshot, "files"));
        assertEquals(3d, kpi(snapshot, "bytes"), 0.001d);
        assertEquals(6d, kpi(snapshot, "today"));
        assertEquals(4d, kpi(snapshot, "recycle"));
        assertEquals(List.of("upload-trend", "bucket-mix"),
                snapshot.charts().stream().map(OverviewChart::key).toList());
        assertTrue(snapshot.alerts().isEmpty());
        assertTrue(snapshot.links().stream().anyMatch(link -> "files".equals(link.key())));
    }

    /**
     * 空间单位：字节折 GB 并带 GB 单位（不是 ¥，也不是裸数字）
     */
    @Test
    @DisplayName("占用空间折 GB 并带 GB 单位")
    void shouldFormatBytesAsGigabytes() {
        stubBaseline(new LibraryStat(10L, 512L * 1024L * 1024L), 0L, new RecycleStat(0L, 0L, 0L),
                List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of()));

        OverviewKpi bytes = snapshot.kpis().stream()
                .filter(item -> "bytes".equals(item.key())).findFirst().orElseThrow();
        assertEquals("GB", bytes.unit());
        assertEquals(0.5d, bytes.value(), 0.001d);
    }

    /**
     * 趋势：只给两天数据，其余四天必须补 0（否则折线会把 6 天前的点接到昨天）
     */
    @Test
    @DisplayName("趋势缺日补 0：有数的天在其位上，其余为 0")
    void shouldFillMissingDaysWithZero() {
        LocalDate today = LocalDate.now();
        stubBaseline(new LibraryStat(1L, 1L), 0L, new RecycleStat(0L, 0L, 0L),
                List.of(new DayCount(today, 5L), new DayCount(today.minusDays(2L), 3L)),
                List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of()));

        OverviewChart trend = chart(snapshot, "upload-trend");
        assertEquals(7, trend.categories().size());
        List<Double> values = trend.series().get(0).values();
        assertEquals(7, values.size());
        assertEquals(5d, values.get(6));
        assertEquals(3d, values.get(4));
        assertEquals(0d, values.get(0));
        assertEquals(0d, values.get(5));
    }

    /**
     * 空库：桶图给一根 0 值柱 + 说明类目，不能给空类目（空类目在屏风上像加载失败）
     */
    @Test
    @DisplayName("空库：桶图给占位类目而不是空图")
    void shouldPlaceholderBucketChartWhenEmpty() {
        stubBaseline(new LibraryStat(0L, 0L), 0L, new RecycleStat(0L, 0L, 0L), List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of()));

        OverviewChart mix = chart(snapshot, "bucket-mix");
        assertEquals(List.of("暂无文件"), mix.categories());
        assertEquals(List.of(0d), mix.series().get(0).values());
    }

    /**
     * 桶太多：前 5 个保留、其余合并「其他」，合计必须等于全部文件数
     */
    @Test
    @DisplayName("桶超过 5 个：合并「其他」且合计守恒")
    void shouldMergeExtraBucketsIntoOther() {
        stubBaseline(new LibraryStat(21L, 0L), 0L, new RecycleStat(0L, 0L, 0L), List.of(), List.of(
                new BucketCount("b1", 6L), new BucketCount("b2", 5L), new BucketCount("b3", 4L),
                new BucketCount("b4", 3L), new BucketCount("b5", 2L), new BucketCount("b6", 1L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of()));

        OverviewChart mix = chart(snapshot, "bucket-mix");
        assertEquals(List.of("b1", "b2", "b3", "b4", "b5", "其他"), mix.categories());
        assertEquals(1d, mix.series().get(0).values().get(5));
        double total = mix.series().get(0).values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(21d, total, 0.001d);
    }

    /**
     * 回收站超期未清理：给 WARNING，文案带保留期天数（值班的人要能对配置）
     */
    @Test
    @DisplayName("回收站超期未清理：WARNING 且文案带保留期天数")
    void shouldWarnStaleRecycle() {
        stubBaseline(new LibraryStat(1L, 0L), 0L, new RecycleStat(5L, 0L, 2L), List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of()));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.WARNING, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("30 天"));
        /* 回收站指标本身要转黄灯：只看告警的话，缩略态（无告警区）就看不出来了 */
        assertEquals("warn", snapshot.kpis().stream()
                .filter(item -> "recycle".equals(item.key())).findFirst().orElseThrow().tone().getValue());
    }

    /**
     * 回收站占用过大：与"超期未清理"**可以并存**（根因不同：一个作业没跑、一个该调保留期）
     */
    @Test
    @DisplayName("回收站占用 ≥5GB：第二条 WARNING 与超期告警并存")
    void shouldWarnRecycleSizeTogetherWithStale() {
        stubBaseline(new LibraryStat(1L, 0L), 0L, new RecycleStat(10L, 6L * GB, 1L), List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of()));

        assertEquals(2, snapshot.alerts().size());
        assertTrue(snapshot.alerts().stream().allMatch(a -> a.level() == OverviewAlertLevel.WARNING));
        assertTrue(snapshot.alerts().stream().anyMatch(a -> a.text().contains("6.0 GB")));
    }

    /**
     * 清理未启用：不告警，只写一条 note（配置成不清理不是故障）
     */
    @Test
    @DisplayName("清理未启用：不报超期告警，只给 note")
    void shouldNotAlertWhenPurgeDisabled() {
        properties.getRecycle().setPurgeEnabled(false);
        stubBaseline(new LibraryStat(1L, 0L), 0L, new RecycleStat(9L, 0L, 9L), List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of()));

        assertTrue(snapshot.alerts().isEmpty());
        assertTrue(snapshot.notes().stream().anyMatch(note -> note.contains("回收站到期清理未启用")));
    }

    /**
     * 保留期为"无限期"（<=0）：不查超期（传 null），也不告警
     */
    @Test
    @DisplayName("保留期 <=0：不查超期且不告警")
    void shouldSkipStaleQueryWhenRetentionDisabled() {
        properties.getRecycle().setRetentionDays(0L);
        when(statQuery.load(anyLong())).thenReturn(new LibraryStat(1L, 0L));
        when(statQuery.countCreated(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);
        when(statQuery.recycle(anyLong(), isNull())).thenReturn(new RecycleStat(9L, 0L, 0L));
        when(statQuery.countCreatedByDay(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(statQuery.byBucket(anyLong())).thenReturn(List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of()));

        assertTrue(snapshot.alerts().isEmpty());
        verify(statQuery, never()).recycle(anyLong(), any(LocalDateTime.class));
        verify(statQuery).recycle(anyLong(), isNull());
    }

    /**
     * 无租户：四个数都是 0，必须写明"未识别租户"，否则看的人以为这个租户一份文件都没有
     */
    @Test
    @DisplayName("无租户上下文：空口径 + 说明，不崩")
    void shouldExplainMissingTenant() {
        when(statQuery.load(isNull())).thenReturn(new LibraryStat(0L, 0L));
        when(statQuery.countCreated(isNull(), any(), any())).thenReturn(0L);
        when(statQuery.recycle(isNull(), any(LocalDateTime.class))).thenReturn(new RecycleStat(0L, 0L, 0L));
        when(statQuery.countCreatedByDay(isNull(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(statQuery.byBucket(isNull())).thenReturn(List.of());

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(1L, null, Set.of("sys:file:list")));

        assertEquals(0d, kpi(snapshot, "files"));
        assertTrue(snapshot.notes().stream().anyMatch(note -> note.contains("未识别租户上下文")));
        /* 入口仍下发：入口判的是"你有没有权限看这张表"，租户缺失只影响数据口径，
           不影响权限本身（与枢密殿/度支殿同一处理）。 */
        assertTrue(snapshot.links().stream().anyMatch(link -> "files".equals(link.key())));
    }

    /**
     * 无文件权限：不下发文件台账入口
     */
    @Test
    @DisplayName("无文件权限：不下发入口")
    void shouldHideLinkWithoutPermission() {
        stubBaseline(new LibraryStat(1L, 0L), 0L, new RecycleStat(0L, 0L, 0L), List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:user:list")));

        assertFalse(snapshot.links().stream().anyMatch(link -> "files".equals(link.key())));
    }

    /**
     * 造上下文
     *
     * @param permissions 权限码
     * @return 上下文
     */
    private OverviewContext contextWith(Set<String> permissions) {
        return OverviewContext.of(1L, TENANT_ID, permissions);
    }

    /**
     * 桩：五个统计口全部按参数返回
     *
     * @param library 库存总量
     * @param today 今日新增
     * @param recycle 回收站统计
     * @param trend 按天趋势
     * @param buckets 按桶统计
     */
    private void stubBaseline(LibraryStat library, long today, RecycleStat recycle,
                              List<DayCount> trend, List<BucketCount> buckets) {
        when(statQuery.load(anyLong())).thenReturn(library);
        when(statQuery.countCreated(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(today);
        when(statQuery.recycle(anyLong(), any(LocalDateTime.class))).thenReturn(recycle);
        when(statQuery.countCreatedByDay(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(trend);
        when(statQuery.byBucket(anyLong())).thenReturn(buckets);
    }

    /**
     * 取指标值
     *
     * @param snapshot 快照
     * @param key 指标键
     * @return 数值
     */
    private double kpi(OverviewSnapshot snapshot, String key) {
        return snapshot.kpis().stream().filter(item -> key.equals(item.key()))
                .findFirst().orElseThrow().value();
    }

    /**
     * 取图表
     *
     * @param snapshot 快照
     * @param key 图表键
     * @return 图表
     */
    private OverviewChart chart(OverviewSnapshot snapshot, String key) {
        return snapshot.charts().stream().filter(item -> key.equals(item.key()))
                .findFirst().orElseThrow();
    }
}
