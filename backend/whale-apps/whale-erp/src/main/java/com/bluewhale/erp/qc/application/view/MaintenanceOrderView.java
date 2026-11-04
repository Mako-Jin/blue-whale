package com.bluewhale.erp.qc.application.view;

import com.bluewhale.erp.qc.domain.model.MaintenanceOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保养工单视图。
 *
 * @author BlueWhale
 */
@Data
public class MaintenanceOrderView {

    /** 主键 */
    private Long id;

    /** 保养工单号 */
    private String orderNo;

    /** 设备编码 */
    private String equipmentNo;

    /** 保养计划主键 */
    private Long planId;

    /** 类型 */
    private String maintenanceType;

    /** 状态 */
    private String status;

    /** 计划保养日期 */
    private LocalDate planDate;

    /** 实际完成日期 */
    private LocalDate completeDate;

    /** 保养费用 */
    private BigDecimal costAmount;

    /** 保养结果 */
    private String result;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param order 保养工单
     * @return 视图
     */
    public static MaintenanceOrderView of(MaintenanceOrder order) {
        MaintenanceOrderView view = new MaintenanceOrderView();
        view.setId(order.getId());
        view.setOrderNo(order.getOrderNo());
        view.setEquipmentNo(order.getEquipmentNo());
        view.setPlanId(order.getPlanId());
        view.setMaintenanceType(order.getMaintenanceType());
        view.setStatus(order.getStatus());
        view.setPlanDate(order.getPlanDate());
        view.setCompleteDate(order.getCompleteDate());
        view.setCostAmount(order.getCostAmount());
        view.setResult(order.getResult());
        view.setRemark(order.getRemark());
        return view;
    }
}
