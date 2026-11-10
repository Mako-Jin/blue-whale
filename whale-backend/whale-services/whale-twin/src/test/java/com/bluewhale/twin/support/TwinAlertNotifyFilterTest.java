package com.bluewhale.twin.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 告警外发过滤器单测：级别门槛、冷却窗口与键隔离。
 *
 * @author BlueWhale
 */
class TwinAlertNotifyFilterTest {

    private static final Long SCENE_ID = 100L;

    private static final long NOW = 1_700_000_000_000L;

    @Test
    void normalLevelNeverPasses() {
        TwinAlertNotifyFilter filter = new TwinAlertNotifyFilter("warn", 300);

        assertFalse(filter.accept(SCENE_ID, "color", "normal", NOW));
    }

    @Test
    void levelBelowThresholdIsRejected() {
        TwinAlertNotifyFilter filter = new TwinAlertNotifyFilter("critical", 0);

        assertFalse(filter.accept(SCENE_ID, "color", "warn", NOW));
        assertTrue(filter.accept(SCENE_ID, "color", "critical", NOW));
    }

    @Test
    void repeatWithinCooldownIsSuppressed() {
        TwinAlertNotifyFilter filter = new TwinAlertNotifyFilter("warn", 300);

        assertTrue(filter.accept(SCENE_ID, "color", "warn", NOW));
        assertFalse(filter.accept(SCENE_ID, "color", "warn", NOW + 299_000L));
        assertTrue(filter.accept(SCENE_ID, "color", "warn", NOW + 300_000L));
    }

    @Test
    void escalationBypassesCooldownBecauseLevelIsPartOfKey() {
        TwinAlertNotifyFilter filter = new TwinAlertNotifyFilter("warn", 300);

        assertTrue(filter.accept(SCENE_ID, "color", "warn", NOW));
        // 升级到 critical 属于不同键，必须立即外发
        assertTrue(filter.accept(SCENE_ID, "color", "critical", NOW + 1_000L));
    }

    @Test
    void differentStateKeyIsIndependent() {
        TwinAlertNotifyFilter filter = new TwinAlertNotifyFilter("warn", 300);

        assertTrue(filter.accept(SCENE_ID, "color", "warn", NOW));
        assertTrue(filter.accept(SCENE_ID, "temperature", "warn", NOW));
    }

    @Test
    void zeroCooldownNeverSuppresses() {
        TwinAlertNotifyFilter filter = new TwinAlertNotifyFilter("warn", 0);

        assertTrue(filter.accept(SCENE_ID, "color", "warn", NOW));
        assertTrue(filter.accept(SCENE_ID, "color", "warn", NOW));
    }

    @Test
    void invalidMinLevelFallsBackToWarn() {
        TwinAlertNotifyFilter filter = new TwinAlertNotifyFilter("bogus", 0);

        assertTrue(filter.accept(SCENE_ID, "color", "warn", NOW));
        assertFalse(filter.accept(SCENE_ID, "color", "normal", NOW));
    }

    @Test
    void recoveryIsRejectedWhenNothingWasNotified() {
        TwinAlertNotifyFilter filter = new TwinAlertNotifyFilter("warn", 300);

        // 从未通知过该点位，恢复不该凭空发一条
        assertNull(filter.acceptRecovery(SCENE_ID, "color"));
    }

    @Test
    void recoveryReportsLevelThatWasActuallyNotified() {
        TwinAlertNotifyFilter filter = new TwinAlertNotifyFilter("warn", 300);

        filter.accept(SCENE_ID, "color", "warn", NOW);
        filter.accept(SCENE_ID, "color", "critical", NOW + 1_000L);

        // 以最近一次实际外发的级别为准
        assertEquals("critical", filter.acceptRecovery(SCENE_ID, "color"));
        // 恢复后点位回到未通知态，重复恢复不再放行
        assertNull(filter.acceptRecovery(SCENE_ID, "color"));
        assertFalse(filter.isNotified(SCENE_ID, "color"));
    }

    @Test
    void recoveryClearsCooldownSoRelapseNotifiesImmediately() {
        TwinAlertNotifyFilter filter = new TwinAlertNotifyFilter("warn", 300);

        assertTrue(filter.accept(SCENE_ID, "color", "warn", NOW));
        assertEquals("warn", filter.acceptRecovery(SCENE_ID, "color"));
        // 恢复即一轮结束：同样的 warn 立刻复发也要重新通知，不能被冷却吞掉
        assertTrue(filter.accept(SCENE_ID, "color", "warn", NOW + 10_000L));
    }

    @Test
    void recoveryOnlyAppliesToTheSamePoint() {
        TwinAlertNotifyFilter filter = new TwinAlertNotifyFilter("warn", 300);

        filter.accept(SCENE_ID, "color", "warn", NOW);

        assertNull(filter.acceptRecovery(SCENE_ID, "temperature"));
        assertEquals("warn", filter.acceptRecovery(SCENE_ID, "color"));
    }
}
