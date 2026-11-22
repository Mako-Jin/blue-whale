package com.bluewhale.map.overview;

import java.time.LocalDate;
import java.util.List;

/**
 * 舆图司（MAP）一次统计快照。
 *
 * <p>四笔资产（打点 / 围栏 / 解析缓存 / 今日调用）各占一张牌，柱图另用一份数据：
 * <b>近七日的调用量</b>。存量说明"舆图上画着多少东西"，调用量说明"这张舆图还有没有人用"——
 * 一个地图服务最危险的死法不是报错，而是没人再问它，屏风上却照样画得满满当当。</p>
 *
 * <p>围栏的两个数分开：{@link #fenceTotal()} 是未删除的全部，{@link #fenceEnabled()}
 * 只数启用中的。停用的围栏仍留在台账里（历史单据要指得回一个名字），但它不参与范围判定，
 * 把它算进"有效疆界"会让舆图看起来比实际管的范围大。</p>
 *
 * @param locationTotal  业务打点总数（未删除，按 bizType/bizId 唯一）
 * @param fenceTotal     围栏总数（未删除，含停用）
 * @param fenceEnabled   启用中的围栏数
 * @param fenceCircle    圆形围栏数（启用 + 停用都算，看的是台账构成）
 * @param fencePolygon   多边形围栏数
 * @param cacheTotal     地址解析缓存条数（含已过期未清的行）
 * @param cacheExpired   其中已过期的条数（过期不等于要清：命中时会被覆盖）
 * @param quotaUsedToday 今日已用调用次数（各供应商合计）
 * @param quotaLimitToday 今日上限合计（各供应商 {@code limit_count} 相加；0 表示不限）
 * @param providersToday 今日有配额台账的供应商数
 * @param recentDays     近 7 日调用量，按日期升序（最早的一天在前，今天在最后）
 * @author BlueWhale
 */
public record MapOverviewStats(
        long locationTotal,
        long fenceTotal,
        long fenceEnabled,
        long fenceCircle,
        long fencePolygon,
        long cacheTotal,
        long cacheExpired,
        long quotaUsedToday,
        long quotaLimitToday,
        int providersToday,
        List<DailyUsage> recentDays) {

    /**
     * 近七日中的一天。
     *
     * @param day 日期
     * @param used 当日调用次数（各供应商合计）
     */
    public record DailyUsage(LocalDate day, long used) {
    }

    /**
     * 停用中的围栏数。
     *
     * @return 停用围栏数
     */
    public long fenceDisabled() {
        return Math.max(0L, fenceTotal - fenceEnabled);
    }
}
