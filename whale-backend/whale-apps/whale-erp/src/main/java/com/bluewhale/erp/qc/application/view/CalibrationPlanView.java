package com.bluewhale.erp.qc.application.view;

import com.bluewhale.erp.qc.domain.model.CalibrationPlan;
import lombok.Data;

import java.time.LocalDate;

/**
 * 校准计划视图。
 *
 * @author BlueWhale
 */
@Data
public class CalibrationPlanView {

    /** 主键 */
    private Long id;

    /** 校准计划号 */
    private String planNo;

    /** 量具编号 */
    private String toolNo;

    /** 校准周期（月） */
    private Integer cycleMonths;

    /** 下次校准日期 */
    private LocalDate nextDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param plan 校准计划
     * @return 视图
     */
    public static CalibrationPlanView of(CalibrationPlan plan) {
        CalibrationPlanView view = new CalibrationPlanView();
        view.setId(plan.getId());
        view.setPlanNo(plan.getPlanNo());
        view.setToolNo(plan.getToolNo());
        view.setCycleMonths(plan.getCycleMonths());
        view.setNextDate(plan.getNextDate());
        view.setStatus(plan.getStatus());
        view.setRemark(plan.getRemark());
        return view;
    }
}
