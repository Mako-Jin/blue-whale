package com.bluewhale.twin.support;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警外发过滤器（Wave 6.10 M0 + 6.11 M0）：级别门槛 + 同键冷却 + 恢复通知准入。
 *
 * <p>规则引擎的「持续确认 + 回差」已能滤掉瞬时毛刺，但缓慢抖动（warn → normal →
 * warn）仍会周期性触发级别变化；冷却窗口保证同场景同状态键的同级别通知不会刷屏。
 * 键为 {@code sceneId:stateKey:level}——升级（warn → critical）属于不同键，会立即放行。</p>
 *
 * <p>6.11 增加「已通知级别」表（键 {@code sceneId:stateKey}）：恢复（normal）要不要外发，
 * 取决于这次告警<b>当初有没有真的通知出去</b>——门槛以下的告警恢复时不该凭空发一条
 * 「已恢复」；恢复放行后同时清掉该点位的冷却位，避免「恢复后立刻复发」被冷却吞掉。</p>
 *
 * <p>{@link #accept} 与 {@link #acceptRecovery} 都加了 {@code synchronized}：判定与写入
 * 必须原子，否则并发 tick 可能同时放行同一条告警。</p>
 *
 * @author BlueWhale
 */
public class TwinAlertNotifyFilter {

    /** 级别权重：低于门槛不外发，normal（恢复）不参与常规外发 */
    private static final Map<String, Integer> LEVEL_RANK = Map.of("warn", 1, "critical", 2);

    /** 冷却表条数上限（超出时按「已过冷却期」清理，避免长跑无界） */
    private static final int MAX_ENTRIES = 4096;

    /** 最低外发级别权重 */
    private final int minRank;

    /** 冷却时长（毫秒；0 表示不去重） */
    private final long cooldownMillis;

    /** 上次放行时间表（键：sceneId:stateKey:level） */
    private final Map<String, Long> lastSentAt = new HashMap<>();

    /** 已实际通知的级别表（键：sceneId:stateKey；超过上限按插入序淘汰最旧） */
    private final Map<String, String> notifiedLevel = new LinkedHashMap<>(16, 0.75f, false) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

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
     * 判断本次级别变化是否该外发（放行即占用冷却位并记下已通知级别）。
     *
     * @param sceneId   场景ID（可空）
     * @param stateKey  实体状态键（可空）
     * @param level     当前级别
     * @param nowMillis 当前时间戳（毫秒）
     * @return 需要外发返回 {@code true}
     */
    public synchronized boolean accept(Long sceneId, String stateKey, String level, long nowMillis) {
        String normalized = level == null ? "" : level.trim().toLowerCase();
        int rank = LEVEL_RANK.getOrDefault(normalized, 0);
        if (rank == 0 || rank < minRank) {
            return false;
        }
        String key = cooldownKey(sceneId, stateKey, normalized);
        Long last = lastSentAt.get(key);
        if (last != null && nowMillis - last < cooldownMillis) {
            return false;
        }
        lastSentAt.put(key, nowMillis);
        notifiedLevel.put(pointKey(sceneId, stateKey), normalized);
        pruneIfNeeded(nowMillis);
        return true;
    }

    /**
     * 判断恢复通知是否该外发。
     *
     * <p>只有「这次告警当初确实通知过」才回一条恢复；放行后清掉该点位的已通知级别与
     * 所有级别的冷却位——恢复代表一轮告警结束，下一次复发应当重新通知。</p>
     *
     * @param sceneId  场景ID（可空）
     * @param stateKey 实体状态键（可空）
     * @return 当初通知过的级别（warn / critical）；从未通知过返回 {@code null}
     */
    public synchronized String acceptRecovery(Long sceneId, String stateKey) {
        String point = pointKey(sceneId, stateKey);
        String previous = notifiedLevel.remove(point);
        if (previous == null) {
            return null;
        }
        String prefix = point + ":";
        lastSentAt.keySet().removeIf(key -> key.startsWith(prefix));
        return previous;
    }

    /**
     * 该点位是否处于「已通知未恢复」状态（仅供排障观测）。
     *
     * @param sceneId  场景ID（可空）
     * @param stateKey 实体状态键（可空）
     * @return 处于已通知状态返回 {@code true}
     */
    public synchronized boolean isNotified(Long sceneId, String stateKey) {
        return notifiedLevel.containsKey(pointKey(sceneId, stateKey));
    }

    /**
     * 冷却键：场景 + 状态键 + 级别。
     *
     * @param sceneId  场景ID
     * @param stateKey 状态键
     * @param level    级别（已归一）
     * @return 冷却键
     */
    private String cooldownKey(Long sceneId, String stateKey, String level) {
        return pointKey(sceneId, stateKey) + ":" + level;
    }

    /**
     * 点位键：场景 + 状态键。
     *
     * @param sceneId  场景ID
     * @param stateKey 状态键
     * @return 点位键
     */
    private String pointKey(Long sceneId, String stateKey) {
        return (sceneId == null ? "-" : sceneId) + ":" + (stateKey == null ? "-" : stateKey);
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
