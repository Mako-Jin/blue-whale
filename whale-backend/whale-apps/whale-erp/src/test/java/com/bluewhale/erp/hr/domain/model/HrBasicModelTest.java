package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OrgUnit}、{@link AttendanceSummary} 与 {@link Kpi} 不变量单元测试。
 *
 * @author BlueWhale
 */
class HrBasicModelTest {

    @Test
    @DisplayName("组织编码重复为上级时拒绝")
    void shouldRejectSelfParent() {
        OrgUnit org = new OrgUnit();
        org.setCode("ORG-01");
        org.setName("总部");
        org.setParentCode("ORG-01");

        BizException exception = assertThrows(BizException.class, org::validate);

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("组织启用状态判定")
    void shouldCheckEnabled() {
        OrgUnit org = new OrgUnit();
        org.setCode("ORG-01");
        org.setName("总部");
        org.setStatus(HrStatus.ORG_ENABLED);

        assertTrue(org.enabled());

        org.setStatus(HrStatus.ORG_DISABLED);
        assertFalse(org.enabled());
    }

    @Test
    @DisplayName("考勤期间缺失拒绝")
    void shouldRejectAttendanceWithoutPeriod() {
        AttendanceSummary attendance = new AttendanceSummary();
        attendance.setEmployeeNo("E001");

        BizException exception = assertThrows(BizException.class, attendance::validate);

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("考勤负数拒绝")
    void shouldRejectNegativeAttendance() {
        AttendanceSummary attendance = new AttendanceSummary();
        attendance.setEmployeeNo("E001");
        attendance.setPeriod("2026-11");
        attendance.setLeaveHours(new BigDecimal("-1"));

        BizException exception = assertThrows(BizException.class, attendance::validate);

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("指标权重超过 100 拒绝")
    void shouldRejectKpiWeightOverLimit() {
        Kpi kpi = new Kpi();
        kpi.setName("交付及时率");
        kpi.setWeight(new BigDecimal("101"));

        BizException exception = assertThrows(BizException.class, kpi::validate);

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("指标名称为空拒绝")
    void shouldRejectKpiWithoutName() {
        Kpi kpi = new Kpi();
        kpi.setWeight(BigDecimal.TEN);

        BizException exception = assertThrows(BizException.class, kpi::validate);

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }
}
