package com.bluewhale.twin.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bluewhale.twin.entity.TwinAlert;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.entity.TwinMarker;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.entity.TwinSnapshot;
import com.bluewhale.twin.mapper.TwinAlertMapper;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.mapper.TwinMarkerMapper;
import com.bluewhale.twin.mapper.TwinSceneMapper;
import com.bluewhale.twin.mapper.TwinSnapshotMapper;
import com.bluewhale.twin.service.TwinSceneService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 观星台（TWIN）统计口径：场景、星标、告警与发布快照。
 *
 * <p><b>口径与业务侧保持一致，不另立一套。</b>未恢复 = {@code resolved = 0}、
 * 未确认 = 其中 {@code ack = 0}、严重 = 其中 {@code level = 'critical'}——
 * 这三句与 {@code TwinAlertService.summary()} 同源同表；屏风上的数字若与告警
 * 抽屉里的数字对不上，值班的人第一反应是"屏风坏了"，而不是"口径不同"。
 * 这里之所以重算一遍而不直接调 service：概览要的是一屏内的稳定快照，
 * 跨服务调用会把 30s 轮询变成两次往返。</p>
 *
 * <p><b>近七日不写 GROUP BY。</b>{@code twin_alert} 只有
 * {@code (scene_id, create_time)} 与 {@code (scene_id, resolved)} 两个索引，
 * 没有单列 {@code create_time}：按天各写一次 {@code count} 会走七次范围扫描。
 * 改成一次 {@code selectList} 只取 {@code create_time} 列，回来在内存里按日累加——
 * 一周的告警行数有限，比七次扫描更好读，也不用自拼 {@code tenant_id}：
 * 租户条件始终由 MyBatis-Plus 插件追加（漏了就是跨租户串数且不报错）。</p>
 *
 * <p><b>租户 ID 必须显式传入。</b>概览 provider 在独立线程里执行，
 * {@code TenantContext} 为空时租户插件会<b>静默跳过</b>租户条件——屏风上就会出现
 * 全平台的和。传 {@code null} 直接抛：宁可让屏风降级，也不要显示一个错的数。</p>
 *
 * <p>统计只读，不写库：概览被前端按 30s 轮询，任何写操作都会变成"每 30 秒写一次"。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class TwinOverviewStatsService {

    /** 告警趋势窗口（天） */
    static final int TREND_WINDOW_DAYS = 7;

    /** 星标启用 */
    static final int ENABLED = 1;

    /** 未恢复 */
    static final int UNRESOLVED = 0;

    /** 未确认 */
    static final int UNACKED = 0;

    /** 级别：严重 */
    static final String LEVEL_CRITICAL = "critical";

    private final TwinSceneMapper sceneMapper;

    private final TwinEntityMapper entityMapper;

    private final TwinMarkerMapper markerMapper;

    private final TwinAlertMapper alertMapper;

    private final TwinSnapshotMapper snapshotMapper;

    /**
     * 取一次统计快照。
     *
     * @param tenantId 租户 ID（必填）
     * @return 统计快照
     */
    public TwinOverviewStats snapshot(Long tenantId) {
        return snapshot(tenantId, LocalDateTime.now());
    }

    /**
     * 取一次统计快照（时间可注入，便于断言"近七日"窗口边界）。
     *
     * @param tenantId 租户 ID（必填）
     * @param now      当前时刻
     * @return 统计快照
     */
    TwinOverviewStats snapshot(Long tenantId, LocalDateTime now) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for twin overview stats");
        }
        LocalDate today = now.toLocalDate();

        long sceneTotal = count(sceneMapper.selectCount(new LambdaQueryWrapper<TwinScene>()));
        long scenePublished = count(sceneMapper.selectCount(new LambdaQueryWrapper<TwinScene>()
                .eq(TwinScene::getStatus, TwinSceneService.STATUS_PUBLISHED)));
        long sceneDraft = count(sceneMapper.selectCount(new LambdaQueryWrapper<TwinScene>()
                .eq(TwinScene::getStatus, TwinSceneService.STATUS_DRAFT)));
        long entityTotal = count(entityMapper.selectCount(new LambdaQueryWrapper<TwinEntity>()));

        long markerTotal = count(markerMapper.selectCount(new LambdaQueryWrapper<TwinMarker>()));
        long markerEnabled = count(markerMapper.selectCount(new LambdaQueryWrapper<TwinMarker>()
                .eq(TwinMarker::getEnabled, ENABLED)));

        long alertUnresolved = count(alertMapper.selectCount(new LambdaQueryWrapper<TwinAlert>()
                .eq(TwinAlert::getResolved, UNRESOLVED)));
        long alertCritical = count(alertMapper.selectCount(new LambdaQueryWrapper<TwinAlert>()
                .eq(TwinAlert::getResolved, UNRESOLVED)
                .eq(TwinAlert::getLevel, LEVEL_CRITICAL)));
        long alertUnacked = count(alertMapper.selectCount(new LambdaQueryWrapper<TwinAlert>()
                .eq(TwinAlert::getResolved, UNRESOLVED)
                .eq(TwinAlert::getAck, UNACKED)));

        long snapshotTotal = count(snapshotMapper.selectCount(new LambdaQueryWrapper<TwinSnapshot>()));
        long snapshotRecent = count(snapshotMapper.selectCount(new LambdaQueryWrapper<TwinSnapshot>()
                .ge(TwinSnapshot::getPublishTime, today.minusDays(TREND_WINDOW_DAYS - 1L).atStartOfDay())));

        return new TwinOverviewStats(sceneTotal, scenePublished, sceneDraft, entityTotal,
                markerTotal, markerEnabled, alertUnresolved, alertCritical, alertUnacked,
                snapshotTotal, snapshotRecent, recentAlerts(today));
    }

    /**
     * 近七日逐日告警数。
     *
     * <p>只取 {@code create_time} 一列：概览不需要告警内容，把 {@code alert_value}
     * 与 {@code thresholds_json} 一起拉回来只是白占带宽。</p>
     *
     * <p><b>这里用字符串列名的 {@link QueryWrapper}，不用 Lambda。</b>
     * {@code LambdaQueryWrapper.select(Entity::getCreateTime)} 会在构造时就去解析
     * 实体列缓存，单测里没有 MyBatis 上下文会直接抛
     * "can not find lambda cache"；字符串列名在两种环境里都只是拼进 SQL，
     * 租户条件仍由插件追加，口径不受影响。</p>
     *
     * @param today 今天
     * @return 七天数据，最早的一天在前
     */
    private List<TwinOverviewStats.DailyAlerts> recentAlerts(LocalDate today) {
        LocalDate from = today.minusDays(TREND_WINDOW_DAYS - 1L);
        List<TwinAlert> rows = alertMapper.selectList(new QueryWrapper<TwinAlert>()
                .select("create_time")
                .ge("create_time", from.atStartOfDay()));
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (TwinAlert row : rows) {
            if (row == null || row.getCreateTime() == null) {
                continue;
            }
            byDay.merge(row.getCreateTime().toLocalDate(), 1L, Long::sum);
        }
        List<TwinOverviewStats.DailyAlerts> days = new ArrayList<>(TREND_WINDOW_DAYS);
        for (int back = TREND_WINDOW_DAYS - 1; back >= 0; back--) {
            LocalDate day = today.minusDays(back);
            days.add(new TwinOverviewStats.DailyAlerts(day, byDay.getOrDefault(day, 0L)));
        }
        return days;
    }

    /**
     * count 结果兜底：MyBatis-Plus 在无行时可能给 {@code null}。
     *
     * @param value 计数
     * @return 非空计数
     */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
