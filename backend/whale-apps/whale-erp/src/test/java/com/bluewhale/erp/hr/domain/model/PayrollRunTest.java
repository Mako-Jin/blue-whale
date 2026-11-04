package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link PayrollRun} 状态机与汇总单元测试。
 *
 * @author BlueWhale
 */
class PayrollRunTest {

    @Test
    @DisplayName("草稿批次计算后汇总应发、社保、公积金、个税与实发")
    void shouldCalculate() {
        PayrollRun run = draftRun();

        run.calculate(List.of(payslip("10000", "1050", "700", "215", "9035")));

        assertEquals(HrStatus.PAYROLL_CALCULATED, run.getStatus());
        assertEquals(1, run.getEmployeeCount());
        assertEquals(0, run.getTotalGross().compareTo(new BigDecimal("10000")));
        assertEquals(0, run.getTotalSocialInsurance().compareTo(new BigDecimal("1050")));
        assertEquals(0, run.getTotalHousingFund().compareTo(new BigDecimal("700")));
        assertEquals(0, run.getTotalTax().compareTo(new BigDecimal("215")));
        assertEquals(0, run.getTotalNet().compareTo(new BigDecimal("9035")));
    }

    @Test
    @DisplayName("无在职员工时计算拒绝")
    void shouldRejectEmptyPayslips() {
        PayrollRun run = draftRun();

        BizException exception = assertThrows(BizException.class, () -> run.calculate(List.of()));

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("非草稿批次不能重复计算")
    void shouldRejectCalculateOnCalculated() {
        PayrollRun run = draftRun();
        run.calculate(List.of(payslip("10000", "0", "0", "0", "10000")));

        BizException exception = assertThrows(BizException.class,
                () -> run.calculate(List.of(payslip("10000", "0", "0", "0", "10000"))));

        assertEquals(ErpErrorCode.HR_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("计算-审批-发放-结账状态流转")
    void shouldFollowLifecycle() {
        PayrollRun run = draftRun();
        run.calculate(List.of(payslip("10000", "0", "0", "0", "10000")));

        run.approve();
        assertEquals(HrStatus.PAYROLL_APPROVED, run.getStatus());

        run.pay();
        assertEquals(HrStatus.PAYROLL_PAID, run.getStatus());

        run.close();
        assertEquals(HrStatus.PAYROLL_CLOSED, run.getStatus());
    }

    @Test
    @DisplayName("已审批批次不能取消")
    void shouldRejectCancelAfterApprove() {
        PayrollRun run = draftRun();
        run.calculate(List.of(payslip("10000", "0", "0", "0", "10000")));
        run.approve();

        BizException exception = assertThrows(BizException.class, run::cancel);

        assertEquals(ErpErrorCode.HR_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("草稿批次可取消")
    void shouldCancelDraft() {
        PayrollRun run = draftRun();

        run.cancel();

        assertEquals(HrStatus.PAYROLL_CANCELLED, run.getStatus());
    }

    /**
     * 构造草稿薪资批次。
     *
     * @return 批次
     */
    private PayrollRun draftRun() {
        PayrollRun run = new PayrollRun();
        run.setId(1L);
        run.setRunNo("PR-202611-0001");
        run.setPeriod("2026-11");
        run.setStatus(HrStatus.PAYROLL_DRAFT);
        return run;
    }

    /**
     * 构造工资条。
     *
     * @param gross 应发
     * @param social 社保
     * @param fund 公积金
     * @param tax 个税
     * @param net 实发
     * @return 工资条
     */
    private Payslip payslip(String gross, String social, String fund, String tax, String net) {
        Payslip payslip = new Payslip();
        payslip.setEmployeeNo("E001");
        payslip.setGrossPay(new BigDecimal(gross));
        payslip.setSocialInsurance(new BigDecimal(social));
        payslip.setHousingFund(new BigDecimal(fund));
        payslip.setTax(new BigDecimal(tax));
        payslip.setNetPay(new BigDecimal(net));
        return payslip;
    }
}
