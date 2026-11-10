package com.bluewhale.twin.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警级别状态跟踪器（6.07 M0）：在阈值分级之上叠加<b>持续确认</b>与<b>回差</b>，
 * 把瞬时毛刺与临界抖动挡在告警之外。
 *
 * <p>每个绑定每个状态键维护三项状态：生效级别、待确认候选级别、候选起始时刻。
 * 判定顺序：先按回差修正原始级别（降格需跌破「阈值 - 回差」），再做升格确认
 * （候选级别持续满 {@code confirmSeconds} 才生效）；降格与恢复立即生效。</p>
 *
 * <p>首评语义：首次观测建立基线（normal），若首次即越界且无需确认，则以
 * {@code previous = null} 产生一条变化（与 6.06 口径一致）。</p>
 *
 * @author BlueWhale
 */
public final class TwinAlertTracker {

    /** 绑定ID → (状态键 → 评估状态) */
    private final Map<Long, Map<String, Entry>> bindings = new ConcurrentHashMap<>();

    /**
     * 观测一个绑定本 tick 的分级结果。
     *
     * @param bindingId 绑定ID
     * @param levels    本 tick 的分级结果
     * @param now       当前时刻（epoch 毫秒）
     * @return 生效级别发生变化的状态键列表（含首评越界/升级/降级/恢复）
     */
    public synchronized List<Transition> observe(Long bindingId,
                                                 Collection<TwinThresholdEvaluator.Level> levels,
                                                 long now) {
        List<Transition> result = new ArrayList<>();
        if (levels == null || levels.isEmpty()) {
            return result;
        }
        Map<String, Entry> states =
                bindings.computeIfAbsent(bindingId, key -> new ConcurrentHashMap<>());
        for (TwinThresholdEvaluator.Level level : levels) {
            Entry entry = states.computeIfAbsent(level.stateKey(), key -> new Entry());
            // previous 为空 = 首评（基线尚未建立）
            String previous = entry.level;
            String base = previous == null ? TwinThresholdEvaluator.LEVEL_NORMAL : previous;
            String raw = applyHysteresis(level, base);
            String next = resolveNext(entry, level, raw, base, now);
            if (entry.level == null) {
                entry.level = base;
            }
            if (next.equals(entry.level)) {
                continue;
            }
            String reported = previous == null ? null : entry.level;
            entry.level = next;
            result.add(new Transition(level.stateKey(), next, level.value(),
                    level.thresholds(), reported));
        }
        return result;
    }

    /**
     * 绑定离线或删除：清空该绑定的生效级别与待确认状态。
     *
     * @param bindingId 绑定ID
     */
    public synchronized void reset(Long bindingId) {
        bindings.remove(bindingId);
    }

    /**
     * 只保留给定绑定（清理已删除绑定的残留状态）。
     *
     * @param aliveBindingIds 当前仍存在的绑定ID集合
     */
    public synchronized void retain(Set<Long> aliveBindingIds) {
        bindings.keySet().retainAll(aliveBindingIds);
    }

    /**
     * 按升格确认规则决定下一个生效级别。
     *
     * @param entry 评估状态（待确认候选在此读写）
     * @param level 分级结果
     * @param raw   回差修正后的原始级别
     * @param base  当前生效级别
     * @param now   当前时刻（epoch 毫秒）
     * @return 下一个生效级别
     */
    private static String resolveNext(Entry entry, TwinThresholdEvaluator.Level level,
                                      String raw, String base, long now) {
        if (severity(raw) <= severity(base)) {
            // 降格 / 持平：立即生效（恢复不做持续确认），清待确认候选
            entry.pending = null;
            return raw;
        }
        int confirmSeconds = level.rule() == null ? 0 : level.rule().confirmSeconds();
        if (confirmSeconds <= 0) {
            entry.pending = null;
            return raw;
        }
        if (!raw.equals(entry.pending)) {
            // 候选级别变化（如 warn 升 critical）：重新计时
            entry.pending = raw;
            entry.since = now;
        }
        if (now - entry.since >= confirmSeconds * 1000L) {
            entry.pending = null;
            return raw;
        }
        return base;
    }

    /**
     * 回差修正：拟降格时只有跌破「退出线」（阈值 - 回差）才真正降级。
     *
     * @param level 分级结果
     * @param base  当前生效级别
     * @return 修正后的级别
     */
    private static String applyHysteresis(TwinThresholdEvaluator.Level level, String base) {
        String raw = level.level();
        if (severity(raw) >= severity(base) || TwinThresholdEvaluator.LEVEL_NORMAL.equals(base)) {
            return raw;
        }
        BigDecimal delta = level.rule() == null ? BigDecimal.ZERO : level.rule().recoveryDelta();
        List<BigDecimal> thresholds = level.thresholds();
        if (delta == null || delta.signum() <= 0
                || thresholds == null || thresholds.isEmpty()) {
            return raw;
        }
        BigDecimal value = TwinThresholdEvaluator.toNumber(level.value());
        if (value == null) {
            return raw;
        }
        BigDecimal exitLine = TwinThresholdEvaluator.LEVEL_CRITICAL.equals(base)
                ? thresholds.get(thresholds.size() - 1).subtract(delta)
                : thresholds.get(0).subtract(delta);
        return value.compareTo(exitLine) >= 0 ? base : raw;
    }

    /**
     * 级别严重度（用于升降格判定）。
     *
     * @param level 级别
     * @return normal=0 / warn=1 / critical=2
     */
    private static int severity(String level) {
        if (TwinThresholdEvaluator.LEVEL_CRITICAL.equals(level)) {
            return 2;
        }
        if (TwinThresholdEvaluator.LEVEL_WARN.equals(level)) {
            return 1;
        }
        return 0;
    }

    /**
     * 级别变化（用于组装 SSE alert 帧与落库）。
     *
     * @param stateKey   实体状态键
     * @param level      新生效级别
     * @param value      触发取值
     * @param thresholds 参与判定的阈值
     * @param previous   上一生效级别（首评越界为空）
     */
    public record Transition(String stateKey, String level, Object value,
                             List<BigDecimal> thresholds, String previous) {
    }

    /**
     * 单状态键的评估状态。
     */
    private static final class Entry {

        /** 生效级别（null = 尚未建立基线） */
        private String level;

        /** 待确认候选级别 */
        private String pending;

        /** 候选起始时刻（epoch 毫秒） */
        private long since;
    }
}
