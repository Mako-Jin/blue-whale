package com.bluewhale.twin.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 告警级别跟踪器单测：升格确认、回差防抖、首评语义、恢复即时生效与状态重置。
 *
 * @author BlueWhale
 */
class TwinAlertTrackerTest {

    private static final Long BINDING = 1L;

    @Test
    void immediateEscalationWithoutConfirm() {
        TwinAlertTracker tracker = new TwinAlertTracker();

        var changes = tracker.observe(BINDING, List.of(defaultLevel("warn", 60)), 1_000L);

        assertEquals(1, changes.size());
        assertNull(changes.get(0).previous(), "首评越界 previous 应为空");
        assertEquals("warn", changes.get(0).level());
        assertEquals(60, changes.get(0).value());
        assertTrue(tracker.observe(BINDING, List.of(defaultLevel("warn", 61)), 2_000L)
                .isEmpty(), "同级不产生变化");
    }

    @Test
    void normalBaselineEmitsNothing() {
        TwinAlertTracker tracker = new TwinAlertTracker();

        assertTrue(tracker.observe(BINDING, List.of(defaultLevel("normal", 10)), 0L).isEmpty());

        var changes = tracker.observe(BINDING, List.of(defaultLevel("warn", 60)), 1_000L);

        assertEquals("normal", changes.get(0).previous());
    }

    @Test
    void confirmationRequiresDwellBeforeEscalation() {
        TwinAlertTracker tracker = new TwinAlertTracker();
        TwinThresholdEvaluator.Rule rule = new TwinThresholdEvaluator.Rule(5, BigDecimal.ZERO);

        assertTrue(tracker.observe(BINDING, List.of(ruleLevel(rule, "warn", 60)), 0L).isEmpty());
        assertTrue(tracker.observe(BINDING, List.of(ruleLevel(rule, "warn", 61)), 3_000L).isEmpty(),
                "未满 5 秒不应升格");

        var changes = tracker.observe(BINDING, List.of(ruleLevel(rule, "warn", 62)), 5_000L);

        assertEquals(1, changes.size());
        assertEquals("warn", changes.get(0).level());
    }

    @Test
    void candidateSwitchRestartsTimer() {
        TwinAlertTracker tracker = new TwinAlertTracker();
        TwinThresholdEvaluator.Rule rule = new TwinThresholdEvaluator.Rule(5, BigDecimal.ZERO);

        assertTrue(tracker.observe(BINDING, List.of(ruleLevel(rule, "warn", 60)), 0L).isEmpty());
        assertTrue(tracker.observe(BINDING, List.of(ruleLevel(rule, "critical", 85)), 3_000L).isEmpty());
        assertTrue(tracker.observe(BINDING, List.of(ruleLevel(rule, "critical", 85)), 5_000L).isEmpty(),
                "候选改为 critical 后应重新计时");

        var changes = tracker.observe(BINDING, List.of(ruleLevel(rule, "critical", 85)), 8_000L);

        assertEquals(1, changes.size());
        assertEquals("critical", changes.get(0).level());
    }

    @Test
    void hysteresisHoldsWarnInsideRecoveryBand() {
        TwinAlertTracker tracker = new TwinAlertTracker();
        TwinThresholdEvaluator.Rule rule = new TwinThresholdEvaluator.Rule(0, BigDecimal.valueOf(5));

        tracker.observe(BINDING, List.of(ruleLevel(rule, "warn", 60)), 0L);

        assertTrue(tracker.observe(BINDING, List.of(ruleLevel(rule, "normal", 48)), 1_000L).isEmpty(),
                "48 > 50-5，应维持在 warn");
        var changes = tracker.observe(BINDING, List.of(ruleLevel(rule, "normal", 44)), 2_000L);

        assertEquals(1, changes.size());
        assertEquals("normal", changes.get(0).level());
        assertEquals("warn", changes.get(0).previous());
    }

    @Test
    void hysteresisHoldsCriticalUntilExitLine() {
        TwinAlertTracker tracker = new TwinAlertTracker();
        TwinThresholdEvaluator.Rule rule = new TwinThresholdEvaluator.Rule(0, BigDecimal.valueOf(5));

        tracker.observe(BINDING, List.of(ruleLevel(rule, "critical", 85)), 0L);

        assertTrue(tracker.observe(BINDING, List.of(ruleLevel(rule, "warn", 76)), 1_000L).isEmpty(),
                "76 > 80-5，应维持在 critical");
        var changes = tracker.observe(BINDING, List.of(ruleLevel(rule, "warn", 74)), 2_000L);

        assertEquals(1, changes.size());
        assertEquals("warn", changes.get(0).level());
        assertEquals("critical", changes.get(0).previous());
    }

    @Test
    void recoveryIsImmediateDespiteConfirm() {
        TwinAlertTracker tracker = new TwinAlertTracker();
        TwinThresholdEvaluator.Rule rule = new TwinThresholdEvaluator.Rule(5, BigDecimal.ZERO);

        tracker.observe(BINDING, List.of(ruleLevel(rule, "warn", 60)), 0L);
        tracker.observe(BINDING, List.of(ruleLevel(rule, "warn", 60)), 5_000L);

        var changes = tracker.observe(BINDING, List.of(ruleLevel(rule, "normal", 10)), 6_000L);

        assertEquals(1, changes.size());
        assertEquals("normal", changes.get(0).level());
    }

    @Test
    void resetClearsStateAndRepeatsFirstEvaluation() {
        TwinAlertTracker tracker = new TwinAlertTracker();
        tracker.observe(BINDING, List.of(defaultLevel("warn", 60)), 0L);

        tracker.reset(BINDING);
        var changes = tracker.observe(BINDING, List.of(defaultLevel("warn", 60)), 1_000L);

        assertEquals(1, changes.size());
        assertNull(changes.get(0).previous(), "重置后重新视为首评");
    }

    @Test
    void retainDropsStaleBindings() {
        TwinAlertTracker tracker = new TwinAlertTracker();
        tracker.observe(1L, List.of(defaultLevel("warn", 60)), 0L);
        tracker.observe(2L, List.of(defaultLevel("warn", 60)), 0L);

        tracker.retain(Set.of(1L));
        var changes = tracker.observe(2L, List.of(defaultLevel("warn", 60)), 1_000L);

        assertNull(changes.get(0).previous(), "被清理的绑定重新建立基线");
    }

    @Test
    void emptyLevelsProduceNoChange() {
        TwinAlertTracker tracker = new TwinAlertTracker();

        assertTrue(tracker.observe(BINDING, List.of(), 0L).isEmpty());
        assertTrue(tracker.observe(BINDING, null, 0L).isEmpty());
    }

    /**
     * 构造缺省规则（立即升格 / 无回差）的分级结果。
     *
     * @param level 级别
     * @param value 取值
     * @return 分级结果
     */
    private static TwinThresholdEvaluator.Level defaultLevel(String level, Object value) {
        return ruleLevel(TwinThresholdEvaluator.Rule.DEFAULT, level, value);
    }

    /**
     * 构造带规则的分级结果（阈值固定 [50, 80]）。
     *
     * @param rule  规则
     * @param level 级别
     * @param value 取值
     * @return 分级结果
     */
    private static TwinThresholdEvaluator.Level ruleLevel(TwinThresholdEvaluator.Rule rule,
                                                          String level, Object value) {
        return new TwinThresholdEvaluator.Level("temp", level, value,
                List.of(new BigDecimal("50"), new BigDecimal("80")), rule);
    }
}
