package com.bluewhale.twin.support;

import java.util.HashMap;
import java.util.Map;

/**
 * 告警外发过滤器（Wave 6.10 M0）：级别门槛 + 同键冷却。
 *
 * <p>规则引擎的「持续确认 + 回差」已能滤掉瞬时毛刺，但缓慢抖动（warn → normal →
 * warn）仍会周期性触发级别变化；冷却窗口保证同场景同状态键的同级别通知不会刷屏。
 * 键为 {@code sceneId:stateKey:level}——升级（warn → critical）属于不同键，会立即放行。</p>
 *
 * <p>{@link #accept} 加了 {@code synchronized}：冷却判定与写入必须原子，
 * 否则并发 tick 可能同时放行同一条告警。</p>
 *
 * @author BlueWhale
 */
public class TwinAlertNotifyFilter {

    /** 级别权重：低于门槛不外发，normal（恢复）不参与外发 */
    private static final Map<String, Integer> LEVEL_RANK = Map.of("warn", 1, "critical", 2);

    /** 冷却表条数上限（超出时按「已过冷却期」清理，避免长跑无界） */
    private static final int MAX_ENTRIES = 4096;

    /** 最低外发级别权重 */
    private final int minRank;

    /** 冷却时长（毫秒；0 表示不去重） */
    private final long cooldownMillis;

    /** 上次放行时间表（键：sceneId:stateKey:level） */
    private final Map<String, Long> lastSentAt = new HashMap<>();

    /**
     * 构造过滤器。
     *
     * @param minLevel        最低外发级别（warn / critical；非法值按 warn 处理）
     * @param cooldownSeconds 冷却秒数（负数按 0 处理）
     */
    public TwinAlertNotifyFilter(String minLevel, int cooldownSeconds) {
        String normalized = minLevel == null ? "warn" : minLevel.trim().toLowerCase();
        this.minRank = LEVEL_RANK.getOrDefault(normalized, LEVEL_RANK.get("warn"));
        this.cooldownMillis = Math.max(cooldownSeconds, 0) * 1000L;
    }

    /**
     * 判断本次级别变化是否该外发（放行即占用冷却位）。
     *
     * @param sceneId   场景ID（可空）
     * @param stateKey  实体状态键（可空）
     * @param level     当前级别
     * @param nowMillis 当前时间戳（毫秒）
     * @return 需要外发返回 {@code true}
     */
    public synchronized boolean accept(Long sceneId, String stateKey, String level, long nowMillis) {
        int rank = LEVEL_RANK.getOrDefault(level == null ? "" : level.trim().toLowerCase(), 0);
        if (rank == 0 || rank < minRank) {
            return false;
        }
        String key = (sceneId == null ? "-" : sceneId) + ":" + (stateKey == null ? "-" : stateKey) + ":" + level;
        Long last = lastSentAt.get(key);
        if (last != null && nowMillis - last < cooldownMillis) {
            return false;
        }
        lastSentAt.put(key, nowMillis);
        pruneIfNeeded(nowMillis);
        return true;
    }

    /**
     * 冷却表超限时清理已过冷却期的条目。
     *
     * @param nowMillis 当前时间戳（毫秒）
     */
    private void pruneIfNeeded(long nowMillis) {
        if (lastSentAt.size() <= MAX_ENTRIES) {
            return;
        }
        lastSentAt.entrySet().removeIf(entry -> nowMillis - entry.getValue() >= cooldownMillis);
    }
}
