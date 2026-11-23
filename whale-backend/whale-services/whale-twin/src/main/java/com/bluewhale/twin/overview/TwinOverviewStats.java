package com.bluewhale.twin.overview;

import java.time.LocalDate;
import java.util.List;

/**
 * 观星台（TWIN）一次统计快照。
 *
 * <p>四笔账各占一张牌：场景（有多少座孪生）、星标（场景里插了多少锚点）、
 * 未恢复告警（天象异动还没收）、发布快照（冻结过几版）。柱图另用一份数据：
 * <b>近七日告警触发数</b>。</p>
 *
 * <p><b>为什么主图是告警而不是场景数。</b>场景与星标是台账：搭好一座孪生之后，
 * 它们就一直在那儿，三个月没人看也不会少一个。告警是流量，它按天掉下来——
 * 近七日一根柱都没有，说明设备安安稳稳，或者绑定早就断了、根本没人在评。
 * 两种情况在屏风上的样子必须能被区分：前者 0 条柱且未恢复为 0，后者通常
 * 还有几条老账挂在"未恢复"里。</p>
 *
 * <p>草稿 / 已发布 / 已停用三个状态分开给：观星台真正能"看"的是已发布场景
 * （冻结版），草稿还在改、停用的已经不参与运行。合成一个总数会让人以为
 * 星盘上有东西，点进去才发现全是编辑中的空壳。</p>
 *
 * @param sceneTotal       场景总数（未删除）
 * @param scenePublished   已发布场景数（status=published）
 * @param sceneDraft       草稿场景数（status=draft）
 * @param entityTotal      孪生实体总数（三态场景的实体都在内）
 * @param markerTotal      星标总数（含停用）
 * @param markerEnabled    启用中的星标数（只有这些会画到浑天仪上）
 * @param alertUnresolved  未恢复告警数（resolved=0）
 * @param alertCritical    未恢复中严重（critical）数量
 * @param alertUnacked     未恢复且未确认数量（还要人点头）
 * @param snapshotTotal    发布快照总数（每次发布一行）
 * @param snapshotRecent   近七日发布快照数
 * @param recentDays       近七日告警触发数，按日期升序（最早在前，今天最后）
 * @author BlueWhale
 */
public record TwinOverviewStats(
        long sceneTotal,
        long scenePublished,
        long sceneDraft,
        long entityTotal,
        long markerTotal,
        long markerEnabled,
        long alertUnresolved,
        long alertCritical,
        long alertUnacked,
        long snapshotTotal,
        long snapshotRecent,
        List<DailyAlerts> recentDays) {

    /**
     * 近七日中的一天。
     *
     * @param day   日期
     * @param count 当日告警触发行数（含已恢复：那一天确实响过）
     */
    public record DailyAlerts(LocalDate day, long count) {
    }

    /**
     * 已停用场景数：总数减去已发布与草稿。
     *
     * <p>不用再查一次库：三态之外没有第四种状态（服务层状态机只有
     * draft → published ⇄ disabled），差出来的就是停用的。</p>
     *
     * @return 已停用场景数
     */
    public long sceneDisabled() {
        return Math.max(0L, sceneTotal - scenePublished - sceneDraft);
    }

    /**
     * 停用中的星标数。
     *
     * @return 停用星标数
     */
    public long markerDisabled() {
        return Math.max(0L, markerTotal - markerEnabled);
    }

    /**
     * 近七日告警合计。
     *
     * @return 七日内触发的告警行数
     */
    public long weekAlerts() {
        return recentDays.stream().mapToLong(DailyAlerts::count).sum();
    }
}
