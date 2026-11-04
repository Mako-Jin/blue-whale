package com.bluewhale.erp.qc.application.view;

import com.bluewhale.erp.qc.domain.model.MaintenancePlan;
import lombok.Data;

import java.time.LocalDate;

/**
 * 保养计划视图。
 *
 * @author BlueWhale
 */
@Data
public class MaintenancePlanView {

    /** 主键 */
    private Long id;

    /** 保养计划号 */
    private String planNo;

    /** 设备编码 */
    private String equipmentNo;

    /** 保养周期（天） */
    private Integer cycleDays;

    /** 上次保养日期 */
    private LocalDate lastDate;

    /** 下次保养日期 */
    private LocalDate nextDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param plan 保养计划
     * @return 视图
     */
    public static MaintenancePlanView of(MaintenancePlan plan) {
        MaintenancePlanView view = new MaintenancePlanView();
        view.setId(plan.getId());
        view.setPlanNo(plan.getPlanNo());
        view.setEquipmentNo(plan.getEquipmentNo());
        view.setCycleDays(plan.getCycleDays());
        view.setLastDate(plan.getLastDate());
        view.setNextDate(plan.getNextDate());
        view.setStatus(plan.getStatus());
        view.setRemark(plan.getRemark());
        return view;
    }
}
