package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备保养工单聚合。
 *
 * <p>不变量：设备编码与计划日期必填；完成时登记费用与结果；不可重复完成或取消已完成工单。</p>
 *
 * @author BlueWhale
 */
@Data
public class MaintenanceOrder {

    /** 主键 */
    private Long id;

    /** 保养工单号 */
    private String orderNo;

    /** 设备编码 */
    private String equipmentNo;

    /** 保养计划主键 */
    private Long planId;

    /** 类型：PREVENTIVE / CORRECTIVE */
    private String maintenanceType;

    /** 状态：OPEN / IN_PROGRESS / COMPLETED / CANCELLED */
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
     * 校验建单数据。
     */
    public void validateForCreate() {
        if (equipmentNo == null || equipmentNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "保养工单设备编码不能为空");
        }
        if (!QcStatus.MAINTENANCE_PREVENTIVE.equals(maintenanceType)
                && !QcStatus.MAINTENANCE_CORRECTIVE.equals(maintenanceType)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID,
                    "保养类型只能为 PREVENTIVE 或 CORRECTIVE");
        }
        if (planDate == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "计划保养日期不能为空");
        }
        costAmount = Amounts.nonNegative(costAmount, "保养费用");
    }

    /**
     * 开始执行保养工单。
     */
    public void start() {
        if (!QcStatus.MAINTENANCE_OPEN.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_STATE_CONFLICT, "只有待执行的保养工单可以开始");
        }
        status = QcStatus.MAINTENANCE_IN_PROGRESS;
    }

    /**
     * 完成保养工单。
     *
     * @param date 实际完成日期
     * @param cost 保养费用
     * @param note 保养结果
     */
    public void complete(LocalDate date, BigDecimal cost, String note) {
        if (QcStatus.MAINTENANCE_COMPLETED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_STATE_CONFLICT, "保养工单已完成，不能重复完成");
        }
        if (QcStatus.MAINTENANCE_CANCELLED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_STATE_CONFLICT, "保养工单已取消，不能完成");
        }
        if (date == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "实际完成日期不能为空");
        }
        if (date.isBefore(planDate)) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "实际完成日期不能早于计划日期");
        }
        completeDate = date;
        costAmount = Amounts.nonNegative(cost, "保养费用");
        result = note;
        status = QcStatus.MAINTENANCE_COMPLETED;
    }
}
