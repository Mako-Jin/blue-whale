package com.bluewhale.map.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.map.entity.MapFence;
import com.bluewhale.map.entity.MapGeocodeCache;
import com.bluewhale.map.entity.MapLocation;
import com.bluewhale.map.entity.MapQuotaUsage;
import com.bluewhale.map.mapper.MapFenceMapper;
import com.bluewhale.map.mapper.MapGeocodeCacheMapper;
import com.bluewhale.map.mapper.MapLocationMapper;
import com.bluewhale.map.mapper.MapQuotaUsageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 舆图司（MAP）统计口径：打点、围栏、解析缓存与供应商调用量。
 *
 * <p><b>为什么主图是"调用量"而不是"打点数"。</b>打点与围栏是台账，只增不减：
 * 一个业务接进来，打点数就一直躺在那儿，哪怕这个业务早就没人查地图了。屏风上若只画
 * 存量，会永远显示一个体面的数字。调用量才是<b>这张舆图还在被使用</b>的证据——
 * 它掉到 0 的时候，打点与围栏一根都不会少。</p>
 *
 * <p><b>配额的口径要连"上限"一起拿出来。</b>{@code used_count} 单独看没有意义：
 * 今天用了 300 次，是"用得很少"还是"配额只有 100 次、早该报警"，取决于
 * {@code limit_count}。所以两张牌分别给"已用"与"上限"，判定线留给 provider——
 * 数字摆出来，语气另说。{@code limit_count = 0} 是"不限"，不参与占比计算，
 * 否则会把所有不限量的供应商算成 0% 或 100% 两个极端。</p>
 *
 * <p><b>近七日不写 GROUP BY。</b>{@code map_quota_usage} 每天每供应商一行，一周最多
 * 十几行——一次 {@code selectList} 取回来在内存里按日累加，比写聚合 SQL 更好读，
 * 也就不用自拼 {@code tenant_id}：租户条件始终由 MyBatis-Plus 插件追加，
 * 一旦漏了就是跨租户串数且不报错（营造成批的老教训）。</p>
 *
 * <p><b>租户 ID 必须显式传入。</b>概览 provider 在独立线程里执行，SDK 会把租户绑到
 * 执行线程（{@code OverviewService} 负责），这里是第二道闸：传 {@code null} 直接抛——
 * 缺租户说明调用方配错了，宁可让屏风降级，也不要在上面显示一个跨租户的和。</p>
 *
 * <p>统计只读，不写库：概览被前端按 30s 轮询，任何写操作都会变成"每 30 秒写一次"。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MapOverviewStatsService {

    /** 调用量趋势窗口（天）：屏风中部放得下七根柱 */
    static final int TREND_WINDOW_DAYS = 7;

    /** 围栏类型：圆 */
    static final String FENCE_CIRCLE = "CIRCLE";

    /** 围栏类型：多边形 */
    static final String FENCE_POLYGON = "POLYGON";

    /** 围栏启用 */
    static final int ENABLED = 1;

    private final MapLocationMapper locationMapper;

    private final MapFenceMapper fenceMapper;

    private final MapGeocodeCacheMapper cacheMapper;

    private final MapQuotaUsageMapper quotaMapper;

    /**
     * 取一次统计快照。
     *
     * @param tenantId 租户 ID（必填）
     * @return 统计快照
     */
    public MapOverviewStats snapshot(Long tenantId) {
        return snapshot(tenantId, LocalDateTime.now());
    }

    /**
     * 取一次统计快照（时间可注入，便于断言"今日"与窗口边界）。
     *
     * @param tenantId 租户 ID（必填）
     * @param now      当前时刻
     * @return 统计快照
     */
    MapOverviewStats snapshot(Long tenantId, LocalDateTime now) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for map overview stats");
        }
        LocalDate today = now.toLocalDate();

        long locationTotal = count(locationMapper.selectCount(new LambdaQueryWrapper<MapLocation>()));
        long fenceTotal = count(fenceMapper.selectCount(new LambdaQueryWrapper<MapFence>()));
        long fenceEnabled = count(fenceMapper.selectCount(new LambdaQueryWrapper<MapFence>()
                .eq(MapFence::getEnabled, ENABLED)));
        long fenceCircle = countByFenceType(FENCE_CIRCLE);
        long fencePolygon = countByFenceType(FENCE_POLYGON);

        long cacheTotal = count(cacheMapper.selectCount(new LambdaQueryWrapper<MapGeocodeCache>()));
        long cacheExpired = count(cacheMapper.selectCount(new LambdaQueryWrapper<MapGeocodeCache>()
                .lt(MapGeocodeCache::getExpireTime, now)));

        List<MapQuotaUsage> usages = quotaMapper.selectList(new LambdaQueryWrapper<MapQuotaUsage>()
                .ge(MapQuotaUsage::getUsageDate, today.minusDays(TREND_WINDOW_DAYS - 1L)));
        long usedToday = 0L;
        long limitToday = 0L;
        int providersToday = 0;
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (MapQuotaUsage usage : usages) {
            if (usage == null || usage.getUsageDate() == null) {
                continue;
            }
            long used = usage.getUsedCount() == null ? 0L : usage.getUsedCount();
            long limit = usage.getLimitCount() == null ? 0L : usage.getLimitCount();
            byDay.merge(usage.getUsageDate(), used, Long::sum);
            if (today.equals(usage.getUsageDate())) {
                usedToday += used;
                /* 0 是"不限"，加进上限合计会把其他供应商的真实上限一起稀释掉 */
                limitToday += Math.max(0L, limit);
                providersToday++;
            }
        }

        List<MapOverviewStats.DailyUsage> recentDays = new ArrayList<>(TREND_WINDOW_DAYS);
        for (int back = TREND_WINDOW_DAYS - 1; back >= 0; back--) {
            LocalDate day = today.minusDays(back);
            recentDays.add(new MapOverviewStats.DailyUsage(day, byDay.getOrDefault(day, 0L)));
        }

        return new MapOverviewStats(locationTotal, fenceTotal, fenceEnabled, fenceCircle, fencePolygon,
                cacheTotal, cacheExpired, usedToday, limitToday, providersToday, recentDays);
    }

    /**
     * 按围栏类型计数。
     *
     * @param fenceType 围栏类型
     * @return 该类型的围栏数
     */
    private long countByFenceType(String fenceType) {
        return count(fenceMapper.selectCount(new LambdaQueryWrapper<MapFence>()
                .eq(MapFence::getFenceType, fenceType)));
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
