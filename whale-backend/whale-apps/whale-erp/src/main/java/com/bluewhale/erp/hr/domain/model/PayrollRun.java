package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 薪资批次聚合。
 *
 * <p>不变量：期间唯一（租户内由仓储唯一键保证）；至少一名员工才可计算；
 * 只有草稿可计算与取消、已计算可审批、已审批可发放、已发放可结账。</p>
 *
 * @author BlueWhale
 */
@Data
public class PayrollRun {

    /** 主键 */
    private Long id;

    /** 批次号 */
    private String runNo;

    /** 薪资期间（YYYY-MM） */
    private String period;

    /** 状态：DRAFT / CALCULATED / APPROVED / PAID / CLOSED / CANCELLED */
    private String status;

    /** 员工人数 */
    private int employeeCount;

    /** 应发合计 */
    private BigDecimal totalGross;

    /** 社保合计（个人部分） */
    private BigDecimal totalSocialInsurance;

    /** 公积金合计（个人部分） */
    private BigDecimal totalHousingFund;

    /** 个税合计 */
    private BigDecimal totalTax;

    /** 实发合计 */
    private BigDecimal totalNet;

    /** 备注 */
    private String remark;

    /** 工资条 */
    private List<Payslip> payslips;

    /**
     * 汇总工资条并置为已计算。
     *
     * @param lines 工资条
     */
    public void calculate(List<Payslip> lines) {
        if (!HrStatus.PAYROLL_DRAFT.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_STATE_CONFLICT, "只有草稿薪资批次可以计算");
        }
        if (lines == null || lines.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "没有可计薪的在职员工");
        }
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal social = BigDecimal.ZERO;
        BigDecimal fund = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        for (Payslip line : lines) {
            gross = gross.add(line.getGrossPay());
            social = social.add(line.getSocialInsurance());
            fund = fund.add(line.getHousingFund());
            tax = tax.add(line.getTax());
            net = net.add(line.getNetPay());
        }
        payslips = lines;
        employeeCount = lines.size();
        totalGross = gross;
        totalSocialInsurance = social;
        totalHousingFund = fund;
        totalTax = tax;
        totalNet = net;
        status = HrStatus.PAYROLL_CALCULATED;
    }

    /**
     * 审批薪资批次（审批后明细不可改）。
     */
    public void approve() {
        if (!HrStatus.PAYROLL_CALCULATED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_STATE_CONFLICT, "只有已计算薪资批次可以审批");
        }
        status = HrStatus.PAYROLL_APPROVED;
    }

    /**
     * 发放薪资。
     */
    public void pay() {
        if (!HrStatus.PAYROLL_APPROVED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_STATE_CONFLICT, "只有已审批薪资批次可以发放");
        }
        status = HrStatus.PAYROLL_PAID;
    }

    /**
     * 结账。
     */
    public void close() {
        if (!HrStatus.PAYROLL_PAID.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_STATE_CONFLICT, "只有已发放薪资批次可以结账");
        }
        status = HrStatus.PAYROLL_CLOSED;
    }

    /**
     * 取消薪资批次（审批后禁止取消）。
     */
    public void cancel() {
        if (!HrStatus.PAYROLL_DRAFT.equals(status) && !HrStatus.PAYROLL_CALCULATED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_STATE_CONFLICT, "已审批薪资批次不能取消");
        }
        status = HrStatus.PAYROLL_CANCELLED;
    }
}
