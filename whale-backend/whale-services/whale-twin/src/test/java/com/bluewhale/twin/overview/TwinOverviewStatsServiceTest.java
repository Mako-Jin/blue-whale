package com.bluewhale.twin.overview;

import com.bluewhale.twin.entity.TwinAlert;
import com.bluewhale.twin.mapper.TwinAlertMapper;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.mapper.TwinMarkerMapper;
import com.bluewhale.twin.mapper.TwinSceneMapper;
import com.bluewhale.twin.mapper.TwinSnapshotMapper;
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
 * {@link TwinOverviewStatsService} 单元测试：租户闸口、三态派生、告警分档与逐日窗口。
 *
 * <p>{@code selectCount} 的入参是包装器对象，按条件回放比断言 SQL 片段稳；
 * 代价是加一次查询就要同步改这里的喂数顺序——这是刻意接受的：派生算错会让
 * 屏风上的三档告警加起来不等于总数，比测试脆弱更难查。</p>
 *
 * @author BlueWhale
 */
class TwinOverviewStatsServiceTest {

    private final TwinSceneMapper sceneMapper = mock(TwinSceneMapper.class);

    private final TwinEntityMapper entityMapper = mock(TwinEntityMapper.class);

    private final TwinMarkerMapper markerMapper = mock(TwinMarkerMapper.class);

    private final TwinAlertMapper alertMapper = mock(TwinAlertMapper.class);

    private final TwinSnapshotMapper snapshotMapper = mock(TwinSnapshotMapper.class);

    private final TwinOverviewStatsService service = new TwinOverviewStatsService(
            sceneMapper, entityMapper, markerMapper, alertMapper, snapshotMapper);

    /**
     * 按统计服务的调用顺序喂场景计数。
     *
     * @param total     场景总数
     * @param published 已发布
     * @param draft     草稿
     */
    private void stubScenes(long total, long published, long draft) {
        when(sceneMapper.selectCount(any())).thenReturn(total, published, draft);
    }

    /**
     * 按调用顺序喂星标计数。
     *
     * @param total   星标总数
     * @param enabled 启用中的星标
     */
    private void stubMarkers(long total, long enabled) {
        when(markerMapper.selectCount(any())).thenReturn(total, enabled);
    }

    /**
     * 按调用顺序喂告警计数：未恢复、其中严重、其中未确认。
     *
     * @param unresolved 未恢复
     * @param critical   其中严重
     * @param unacked    其中未确认
     */
    private void stubAlerts(long unresolved, long critical, long unacked) {
        when(alertMapper.selectCount(any())).thenReturn(unresolved, critical, unacked);
    }

    /**
     * 按调用顺序喂快照计数。
     *
     * @param total  快照总数
     * @param recent 近七日发布
     */
    private void stubSnapshots(long total, long recent) {
        when(snapshotMapper.selectCount(any())).thenReturn(total, recent);
    }

    /**
     * 默认喂一份"刚搭好"的账本：一个空库。
     */
    private void stubEmpty() {
        stubScenes(0L, 0L, 0L);
        when(entityMapper.selectCount(any())).thenReturn(0L);
        stubMarkers(0L, 0L);
        stubAlerts(0L, 0L, 0L);
        stubSnapshots(0L, 0L);
        when(alertMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("缺租户直接抛：宁可让屏风降级，也不显示跨租户的和")
    void shouldRejectMissingTenant() {
        assertThatThrownBy(() -> service.snapshot(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("空库全零：七根柱都在，值全为 0（屏风不能因为没数据就没有横轴）")
    void shouldReturnZerosForEmptyLibrary() {
        stubEmpty();

        TwinOverviewStats stats = service.snapshot(9001L, LocalDateTime.of(2026, 11, 23, 10, 0));

        assertThat(stats.sceneTotal()).isZero();
        assertThat(stats.scenePublished()).isZero();
        assertThat(stats.sceneDisabled()).isZero();
        assertThat(stats.entityTotal()).isZero();
        assertThat(stats.markerEnabled()).isZero();
        assertThat(stats.alertUnresolved()).isZero();
        assertThat(stats.snapshotTotal()).isZero();
        assertThat(stats.recentDays()).hasSize(7);
        assertThat(stats.recentDays()).allMatch(day -> day.count() == 0L);
        assertThat(stats.weekAlerts()).isZero();
    }

    @Test
    @DisplayName("场景三态：已停用 = 总数 − 已发布 − 草稿（不再多查一次库）")
    void shouldDeriveDisabledScenes() {
        stubScenes(10L, 4L, 5L);
        when(entityMapper.selectCount(any())).thenReturn(31L);
        stubMarkers(12L, 9L);
        stubAlerts(2L, 1L, 2L);
        stubSnapshots(6L, 1L);
        when(alertMapper.selectList(any())).thenReturn(List.of());

        TwinOverviewStats stats = service.snapshot(9001L, LocalDateTime.of(2026, 11, 23, 10, 0));

        assertThat(stats.sceneTotal()).isEqualTo(10L);
        assertThat(stats.scenePublished()).isEqualTo(4L);
        assertThat(stats.sceneDraft()).isEqualTo(5L);
        assertThat(stats.sceneDisabled()).isEqualTo(1L);
        assertThat(stats.entityTotal()).isEqualTo(31L);
        assertThat(stats.markerTotal()).isEqualTo(12L);
        assertThat(stats.markerEnabled()).isEqualTo(9L);
        assertThat(stats.markerDisabled()).isEqualTo(3L);
        assertThat(stats.alertUnresolved()).isEqualTo(2L);
        assertThat(stats.alertCritical()).isEqualTo(1L);
        assertThat(stats.alertUnacked()).isEqualTo(2L);
        assertThat(stats.snapshotTotal()).isEqualTo(6L);
        assertThat(stats.snapshotRecent()).isEqualTo(1L);
    }

    @Test
    @DisplayName("脏数据让差值为负时夹到 0，不把负柱画到基线以下")
    void shouldClampNegativeBuckets() {
        stubScenes(1L, 1L, 1L);
        when(entityMapper.selectCount(any())).thenReturn(0L);
        stubMarkers(1L, 3L);
        stubAlerts(0L, 0L, 0L);
        stubSnapshots(0L, 0L);
        when(alertMapper.selectList(any())).thenReturn(List.of());

        TwinOverviewStats stats = service.snapshot(9001L, LocalDateTime.of(2026, 11, 23, 10, 0));

        assertThat(stats.sceneDisabled()).isZero();
        assertThat(stats.markerDisabled()).isZero();
    }

    @Test
    @DisplayName("近七日逐日累加：同一天多行合并，窗口外的日期不落进任何一根柱")
    void shouldBucketAlertsByDay() {
        stubEmpty();
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 10, 0);
        when(alertMapper.selectList(any())).thenReturn(List.of(
                alert(now.withHour(2)),
                alert(now.withHour(9)),
                alert(now.minusDays(3).withHour(15)),
                alert(now.minusDays(8).withHour(4))));

        TwinOverviewStats stats = service.snapshot(9001L, now);

        assertThat(stats.recentDays()).hasSize(7);
        assertThat(stats.recentDays().get(6).day()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(stats.recentDays().get(6).count()).isEqualTo(2L);
        assertThat(stats.recentDays().get(3).count()).isEqualTo(1L);
        assertThat(stats.weekAlerts()).isEqualTo(3L);
    }

    @Test
    @DisplayName("柱图横轴：最早的一天在前，今天在最后，缺数据的日期补 0")
    void shouldReturnSevenDaysAscending() {
        stubEmpty();
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 10, 0);
        when(alertMapper.selectList(any())).thenReturn(List.of());

        TwinOverviewStats stats = service.snapshot(9001L, now);

        assertThat(stats.recentDays().get(0).day()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(stats.recentDays().get(6).day()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(stats.recentDays()).allMatch(day -> day.count() == 0L);
    }

    @Test
    @DisplayName("告警行缺 create_time 时跳过，不往今天塞一个假柱")
    void shouldSkipAlertWithoutCreateTime() {
        stubEmpty();
        when(alertMapper.selectList(any())).thenReturn(List.of(alert(null)));

        TwinOverviewStats stats = service.snapshot(9001L, LocalDateTime.of(2026, 11, 23, 10, 0));

        assertThat(stats.weekAlerts()).isZero();
    }

    @Test
    @DisplayName("计数为 null 时按 0 处理：selectCount 无行时可能给 null")
    void shouldTreatNullCountAsZero() {
        when(sceneMapper.selectCount(any())).thenReturn((Long) null);
        when(entityMapper.selectCount(any())).thenReturn((Long) null);
        when(markerMapper.selectCount(any())).thenReturn((Long) null);
        when(alertMapper.selectCount(any())).thenReturn((Long) null);
        when(snapshotMapper.selectCount(any())).thenReturn((Long) null);
        when(alertMapper.selectList(any())).thenReturn(List.of());

        TwinOverviewStats stats = service.snapshot(9001L, LocalDateTime.of(2026, 11, 23, 10, 0));

        assertThat(stats.sceneTotal()).isZero();
        assertThat(stats.markerTotal()).isZero();
        assertThat(stats.alertUnresolved()).isZero();
        assertThat(stats.snapshotTotal()).isZero();
    }

    /**
     * 造一条只带创建时间的告警行（统计只读这一列）。
     *
     * @param createTime 创建时间（可空）
     * @return 告警行
     */
    private static TwinAlert alert(LocalDateTime createTime) {
        TwinAlert alert = new TwinAlert();
        alert.setCreateTime(createTime);
        return alert;
    }
}
