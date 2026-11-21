package com.bluewhale.iot.dto;

import java.util.List;

/**
 * 兵部坊（IoT）的一次性统计快照。
 *
 * <p><b>为什么单独一组计数，而不是让概览 provider 直接查 Mapper。</b>
 * 概览 provider 属于 3D 城的取数层，一旦它开始自己写查询条件，
 * 「在线」的判定口径就有两份（2D 设备页按 {@code online = 1 AND status = 'active'}，
 * 3D 屏风按 {@code online = 1}），迟早出现"后台说 8 台在线、城里说 9 台"。
 * 这里把所有口径收在 {@code IotStatsService} 一处，provider 只负责排版。</p>
 *
 * <p><b>{@code deviceOnline} 只数启用中的设备</b>：停用设备残留的 {@code online = 1}
 * 是历史心跳痕迹（停用不会改 online 位），把它算进在线率会让"全部停用"看起来像"全部在线"。</p>
 *
 * @param deviceTotal     设备总数（含停用）
 * @param deviceActive    启用中的设备数
 * @param deviceOnline    启用且在线的设备数
 * @param telemetry24h    近 24 小时遥测上报条数
 * @param telemetryDays   近 7 日按天的上报条数（含今天，按日期升序）
 * @param pendingCommands 已下发但未收到回执的指令数（pending + sent）
 * @param rulesTotal      规则总数（含停用）
 * @param rulesEnabled    启用中的规则数
 * @author BlueWhale
 */
public record IotStats(
        long deviceTotal,
        long deviceActive,
        long deviceOnline,
        long telemetry24h,
        List<IotDayCount> telemetryDays,
        long pendingCommands,
        long rulesTotal,
        long rulesEnabled) {

    /**
     * 离线设备数：启用中但不在线。
     *
     * <p>不用 {@code deviceTotal - deviceOnline}：停用设备本来就不该在线，
     * 那样减出来的"离线数"会把停用也算成故障，屏风上天天挂着几百台"离线"。</p>
     *
     * @return 离线设备数（不为负）
     */
    public long offlineDevices() {
        return Math.max(deviceActive - deviceOnline, 0L);
    }

    /**
     * 在线率：在线 ÷ 启用。
     *
     * @return 0–1 的比率；没有启用设备时返回 0
     */
    public double onlineRate() {
        if (deviceActive <= 0) {
            return 0d;
        }
        return (double) deviceOnline / (double) deviceActive;
    }
}
