package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Appraisal} 等级推导与 {@link PerformanceCycle} 状态机单元测试。
 *
 * @author BlueWhale
 */
class AppraisalTest {

    @Test
    @DisplayName("得分按 90/75/60 分档推导等级")
    void shouldGradeByScore() {
        assertEquals("A", Appraisal.gradeOf(new BigDecimal("90")));
        assertEquals("B", Appraisal.gradeOf(new BigDecimal("89.99")));
        assertEquals("B", Appraisal.gradeOf(new BigDecimal("75")));
        assertEquals("C", Appraisal.gradeOf(new BigDecimal("60")));
        assertEquals("D", Appraisal.gradeOf(new BigDecimal("59.99")));
    }

    @Test
    @DisplayName("得分超出 0–100 拒绝")
    void shouldRejectOutOfRangeScore() {
        Appraisal appraisal = appraisal(new BigDecimal("101"));

        BizException exception = assertThrows(BizException.class, appraisal::validateAndGrade);

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("考核指标缺失拒绝")
    void shouldRejectMissingKpi() {
        Appraisal appraisal = appraisal(new BigDecimal("80"));
        appraisal.setKpiId(null);

        BizException exception = assertThrows(BizException.class, appraisal::validateAndGrade);

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("有效得分推导等级")
    void shouldValidateAndGrade() {
        Appraisal appraisal = appraisal(new BigDecimal("86"));

        appraisal.validateAndGrade();

        assertEquals("B", appraisal.getGrade());
    }

    @Test
    @DisplayName("绩效周期草稿启动后不可重复启动")
    void shouldStartOnlyDraft() {
        PerformanceCycle cycle = cycle();

        cycle.start();
        assertEquals(HrStatus.CYCLE_IN_PROGRESS, cycle.getStatus());

        BizException exception = assertThrows(BizException.class, cycle::start);
        assertEquals(ErpErrorCode.HR_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("绩效周期关闭后不可再录入且不可重复关闭")
    void shouldCloseOnce() {
        PerformanceCycle cycle = cycle();
        cycle.start();

        cycle.close();

        assertEquals(HrStatus.CYCLE_CLOSED, cycle.getStatus());
        assertFalse(cycle.appraisable());
        BizException exception = assertThrows(BizException.class, cycle::close);
        assertEquals(ErpErrorCode.HR_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("考核结束日期早于开始日期拒绝")
    void shouldRejectInvalidPeriod() {
        PerformanceCycle cycle = cycle();
        cycle.setEndDate(LocalDate.of(2026, 10, 1));

        BizException exception = assertThrows(BizException.class, cycle::validate);

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    /**
     * 构造考核结果。
     *
     * @param score 得分
     * @return 考核结果
     */
    private Appraisal appraisal(BigDecimal score) {
        Appraisal appraisal = new Appraisal();
        appraisal.setCycleId(1L);
        appraisal.setKpiId(2L);
        appraisal.setEmployeeNo("E001");
        appraisal.setEmployeeName("张三");
        appraisal.setScore(score);
        return appraisal;
    }

    /**
     * 构造绩效周期。
     *
     * @return 周期
     */
    private PerformanceCycle cycle() {
        PerformanceCycle cycle = new PerformanceCycle();
        cycle.setCycleNo("PC-202611-0001");
        cycle.setName("2026 年 11 月绩效");
        cycle.setPeriod("2026-11");
        cycle.setStartDate(LocalDate.of(2026, 11, 1));
        cycle.setEndDate(LocalDate.of(2026, 11, 30));
        cycle.setStatus(HrStatus.CYCLE_DRAFT);
        return cycle;
    }
}
