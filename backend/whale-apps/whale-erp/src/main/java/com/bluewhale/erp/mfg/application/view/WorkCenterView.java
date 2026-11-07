package com.bluewhale.erp.mfg.application.view;

import com.bluewhale.erp.mfg.domain.model.WorkCenter;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工作中心视图。
 *
 * @author BlueWhale
 */
@Data
public class WorkCenterView {

    /** 主键 */
    private Long id;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 所属部门 */
    private String department;

    /** 日产能工时 */
    private BigDecimal dailyCapacityHours;

    /** 效率百分比 */
    private BigDecimal efficiencyRate;

    /** 工作日（ISO 1–7，逗号分隔） */
    private String workDays;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param center 工作中心
     * @return 视图
     */
    public static WorkCenterView of(WorkCenter center) {
        WorkCenterView view = new WorkCenterView();
        view.setId(center.getId());
        view.setCode(center.getCode());
        view.setName(center.getName());
        view.setDepartment(center.getDepartment());
        view.setDailyCapacityHours(center.getDailyCapacityHours());
        view.setEfficiencyRate(center.getEfficiencyRate());
        view.setWorkDays(center.getWorkDays());
        view.setStatus(center.getStatus());
        view.setRemark(center.getRemark());
        return view;
    }
}
