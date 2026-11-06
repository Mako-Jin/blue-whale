package com.bluewhale.lms.integration.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.integration.domain.model.ErpEmployeeEntry;
import com.bluewhale.lms.integration.domain.model.MemberSnapshot;
import com.bluewhale.lms.integration.domain.model.MemberStatus;
import com.bluewhale.lms.integration.domain.model.NotifyKey;
import com.bluewhale.lms.integration.domain.model.NotifyLog;
import com.bluewhale.lms.integration.domain.model.NotifyScene;
import com.bluewhale.lms.integration.domain.model.NotifyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集成域规则：催办可达性、快照同步校验、幂等键口径、通知留痕状态。
 *
 * @author BlueWhale
 */
class IntegrationDomainRuleTest {

    /**
     * 在职且已开通账号才可被催办。
     */
    @Test
    @DisplayName("在职且有账号才可催办")
    void onlyActiveWithAccountIsUrgeable() {
        MemberSnapshot active = snapshot(MemberStatus.ACTIVE, 100L);
        assertTrue(active.isUrgeable());

        MemberSnapshot offboard = snapshot(MemberStatus.LEFT, 100L);
        assertFalse(offboard.isUrgeable(), "离职员工保留记录但不再催办");

        MemberSnapshot noAccount = snapshot(MemberStatus.ACTIVE, null);
        assertFalse(noAccount.isUrgeable(), "未开通账号的人发出去没人收");
    }

    /**
     * 未知状态按离职处理，宁可少发不打扰。
     */
    @Test
    @DisplayName("未知在职状态按离职处理")
    void unknownStatusFallsBackToLeft() {
        assertEquals(MemberStatus.LEFT, MemberStatus.parse(null));
        assertEquals(MemberStatus.LEFT, MemberStatus.parse("RESIGNED"));
        assertEquals(MemberStatus.ACTIVE, MemberStatus.parse(" active "));
    }

    /**
     * 同步校验：员工编号与同步时间必填，非法离职日期自动纠正。
     */
    @Test
    @DisplayName("同步校验拦住缺编号并纠正倒挂日期")
    void syncValidation() {
        MemberSnapshot noCode = new MemberSnapshot();
        assertThrows(BizException.class, () -> noCode.validateForSync(LocalDateTime.now()));

        MemberSnapshot inverted = snapshot(MemberStatus.LEFT, 100L);
        inverted.setHireDate(LocalDate.of(2026, 5, 1));
        inverted.setLeaveDate(LocalDate.of(2026, 1, 1));
        inverted.validateForSync(LocalDateTime.now());
        assertEquals(inverted.getHireDate(), inverted.getLeaveDate(), "倒挂日期按入职日纠正而不是让整批同步失败");
    }

    /**
     * 目录条目转快照时状态与时间落对。
     */
    @Test
    @DisplayName("目录条目转快照")
    void entryToSnapshot() {
        ErpEmployeeEntry entry = new ErpEmployeeEntry();
        entry.setEmployeeNo("E001");
        entry.setUserId(9L);
        entry.setStatus("LEFT");
        entry.setLeaveDate(LocalDate.of(2026, 10, 1));
        LocalDateTime syncedAt = LocalDateTime.of(2026, 11, 6, 9, 0);
        MemberSnapshot snapshot = entry.toSnapshot(syncedAt);
        assertEquals("E001", snapshot.getEmployeeNo());
        assertEquals(MemberStatus.LEFT, snapshot.getStatus());
        assertEquals(syncedAt, snapshot.getLastSyncedAt());
        assertFalse(snapshot.isUrgeable());
    }

    /**
     * 幂等键同场景同对象恒定，按天催办换日换键。
     */
    @Test
    @DisplayName("幂等键口径稳定")
    void notifyKeysAreStable() {
        assertEquals(NotifyKey.enroll(7L), NotifyKey.enroll(7L));
        assertEquals("ENROLL:7", NotifyKey.enroll(7L));
        assertEquals("CLASS_START:9", NotifyKey.classStart(9L));
        assertEquals("CERT_ISSUE:11", NotifyKey.certIssue(11L));
        assertEquals("AUTO_URGE:9:20261106", NotifyKey.urge(9L, LocalDate.of(2026, 11, 6)));
        assertEquals("AUTO_URGE:9:20261107", NotifyKey.urge(9L, LocalDate.of(2026, 11, 7)));
        assertThrows(BizException.class, () -> NotifyKey.enroll(null));
    }

    /**
     * 通知留痕状态流转。
     */
    @Test
    @DisplayName("通知留痕可失败后重试成功")
    void notifyLogStatusFlow() {
        NotifyLog log = new NotifyLog();
        log.setScene(NotifyScene.URGE);
        log.setIdempotencyKey("AUTO_URGE:9:20261106");
        log.setUserId(100L);
        log.validate();
        assertFalse(log.alreadySent());

        LocalDateTime first = LocalDateTime.of(2026, 11, 6, 9, 0);
        log.markFailed("消息服务不可用", first);
        assertEquals(NotifyStatus.FAILED, log.getSendStatus());
        assertEquals("消息服务不可用", log.getFailReason());
        assertFalse(log.alreadySent(), "失败记录必须允许下一轮重试");

        LocalDateTime second = LocalDateTime.of(2026, 11, 6, 10, 0);
        log.markSent(second);
        assertEquals(NotifyStatus.SENT, log.getSendStatus());
        assertNull(log.getFailReason());
        assertTrue(log.alreadySent());
        assertEquals(second, log.getSentAt());
    }

    /**
     * 失败原因超长截断，不撑爆列宽。
     */
    @Test
    @DisplayName("失败原因截断到 500 字符")
    void failReasonTruncated() {
        NotifyLog log = new NotifyLog();
        log.setScene(NotifyScene.URGE);
        log.setIdempotencyKey("K");
        log.setUserId(1L);
        log.markFailed("x".repeat(800), LocalDateTime.now());
        assertEquals(500, log.getFailReason().length());
    }

    /**
     * 缺接收人或缺幂等键直接拒绝。
     */
    @Test
    @DisplayName("留痕必填校验")
    void notifyLogValidation() {
        NotifyLog noUser = new NotifyLog();
        noUser.setScene(NotifyScene.URGE);
        noUser.setIdempotencyKey("K");
        BizException exception = assertThrows(BizException.class, noUser::validate);
        assertEquals(LmsErrorCode.NOTIFY_INVALID, exception.getCode());

        NotifyLog noKey = new NotifyLog();
        noKey.setScene(NotifyScene.URGE);
        noKey.setUserId(1L);
        assertThrows(BizException.class, noKey::validate);

        NotifyLog tooLong = new NotifyLog();
        tooLong.setScene(NotifyScene.URGE);
        tooLong.setUserId(1L);
        tooLong.setIdempotencyKey("k".repeat(200));
        assertThrows(BizException.class, tooLong::validate);
    }

    /**
     * 场景解析与模板编码。
     */
    @Test
    @DisplayName("通知场景解析")
    void notifySceneParse() {
        assertEquals(NotifyScene.URGE, NotifyScene.parse("urge"));
        assertNull(NotifyScene.parse("UNKNOWN"));
        assertEquals("LMS_CERT_ISSUE", NotifyScene.CERT_ISSUE.getTemplateCode());
        assertEquals("证书发放通知", NotifyScene.CERT_ISSUE.getDefaultTitle());
    }

    /**
     * 构造测试用快照。
     *
     * @param status 在职状态
     * @param userId 账号 ID
     * @return 快照
     */
    private MemberSnapshot snapshot(MemberStatus status, Long userId) {
        MemberSnapshot snapshot = new MemberSnapshot();
        snapshot.setEmployeeNo("E001");
        snapshot.setUserId(userId);
        snapshot.setStatus(status);
        return snapshot;
    }
}
