package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 渠道报单冲突判定与裁决不变量测试。
 *
 * @author BlueWhale
 */
@DisplayName("渠道报单聚合")
class PartnerReportTest {

    @Test
    @DisplayName("同客户同项目存在有效报单时进入冲突")
    void conflictDetected() {
        assertEquals(PartnerReportStatus.CONFLICT, PartnerReport.initialStatus(true, false));
        assertEquals(PartnerReportStatus.PENDING, PartnerReport.initialStatus(false, false));
    }

    @Test
    @DisplayName("冲突报单可裁决为核准并记录金额")
    void resolveConflictApproved() {
        PartnerReport report = conflictReport();
        report.resolve(PartnerReportStatus.APPROVED, new BigDecimal("120.00"), 7L,
                LocalDateTime.of(2026, 11, 8, 12, 0));
        assertEquals(PartnerReportStatus.APPROVED, report.getStatus());
        assertEquals(new BigDecimal("120.00"), report.getApprovedAmount());
        assertEquals(7L, report.getResolvedBy());
    }

    @Test
    @DisplayName("非冲突报单不允许裁决")
    void resolveNonConflictRejected() {
        PartnerReport report = new PartnerReport();
        report.setStatus(PartnerReportStatus.PENDING);
        assertThrows(BizException.class, () -> report.resolve(PartnerReportStatus.APPROVED,
                new BigDecimal("100.00"), 7L, LocalDateTime.now()));
    }

    @Test
    @DisplayName("核准冲突报单必须填写金额")
    void approvedWithoutAmountRejected() {
        PartnerReport report = conflictReport();
        assertThrows(BizException.class, () -> report.resolve(PartnerReportStatus.APPROVED, null, 7L,
                LocalDateTime.now()));
    }

    /**
     * 构造冲突状态的报单。
     *
     * @return 报单
     */
    private PartnerReport conflictReport() {
        PartnerReport report = new PartnerReport();
        report.markConflict("同一客户同一项目存在待审核或已核准报单");
        return report;
    }
}
