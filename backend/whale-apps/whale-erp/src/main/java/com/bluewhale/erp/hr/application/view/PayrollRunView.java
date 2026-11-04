package com.bluewhale.erp.hr.application.view;

import com.bluewhale.erp.hr.domain.model.PayrollRun;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 薪资批次视图。
 *
 * @author BlueWhale
 */
@Data
public class PayrollRunView {

    /** 主键 */
    private Long id;

    /** 批次号 */
    private String runNo;

    /** 薪资期间 */
    private String period;

    /** 状态 */
    private String status;

    /** 员工人数 */
    private int employeeCount;

    /** 应发合计 */
    private BigDecimal totalGross;

    /** 社保合计 */
    private BigDecimal totalSocialInsurance;

    /** 公积金合计 */
    private BigDecimal totalHousingFund;

    /** 个税合计 */
    private BigDecimal totalTax;

    /** 实发合计 */
    private BigDecimal totalNet;

    /** 备注 */
    private String remark;

    /** 工资条 */
    private List<PayslipView> payslips;

    /**
     * 由领域对象构建视图。
     *
     * @param run 薪资批次
     * @return 视图
     */
    public static PayrollRunView of(PayrollRun run) {
        PayrollRunView view = new PayrollRunView();
        view.setId(run.getId());
        view.setRunNo(run.getRunNo());
        view.setPeriod(run.getPeriod());
        view.setStatus(run.getStatus());
        view.setEmployeeCount(run.getEmployeeCount());
        view.setTotalGross(run.getTotalGross());
        view.setTotalSocialInsurance(run.getTotalSocialInsurance());
        view.setTotalHousingFund(run.getTotalHousingFund());
        view.setTotalTax(run.getTotalTax());
        view.setTotalNet(run.getTotalNet());
        view.setRemark(run.getRemark());
        if (run.getPayslips() != null) {
            view.setPayslips(run.getPayslips().stream().map(PayslipView::of).toList());
        }
        return view;
    }
}
