package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.time.LocalDate;

/**
 * 设备保养计划聚合。
 *
 * <p>不变量：设备编码必填；保养周期为正；下次保养日 = 上次保养日 + 周期天数。</p>
 *
 * @author BlueWhale
 */
@Data
public class MaintenancePlan {

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

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 校验建档数据。
     */
    public void validateForCreate() {
        if (equipmentNo == null || equipmentNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "保养计划设备编码不能为空");
        }
        if (cycleDays == null || cycleDays <= 0) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "保养周期必须大于 0 天");
        }
        if (nextDate == null && lastDate != null) {
            nextDate = lastDate.plusDays(cycleDays);
        }
        if (nextDate == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "下次保养日期不能为空");
        }
        status = status == null || status.isBlank() ? QcStatus.ENABLED : status;
    }

    /**
     * 保养完成后推进计划：登记上次保养日并顺延下次保养日。
     *
     * @param date 实际保养日期
     */
    public void advance(LocalDate date) {
        if (date == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "实际保养日期不能为空");
        }
        lastDate = date;
        nextDate = date.plusDays(cycleDays);
    }

    /**
     * 判断计划是否启用。
     *
     * @return 启用返回 true
     */
    public boolean enabled() {
        return QcStatus.ENABLED.equals(status);
    }
}
