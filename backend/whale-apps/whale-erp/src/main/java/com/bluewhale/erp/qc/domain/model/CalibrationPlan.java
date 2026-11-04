package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.time.LocalDate;

/**
 * 量具校准计划聚合。
 *
 * <p>不变量：量具编号必填；校准周期为正；下次校准日期必填。</p>
 *
 * @author BlueWhale
 */
@Data
public class CalibrationPlan {

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

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 校验建档数据。
     */
    public void validateForCreate() {
        if (toolNo == null || toolNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "校准计划量具编号不能为空");
        }
        if (cycleMonths == null || cycleMonths <= 0) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "校准周期必须大于 0 个月");
        }
        if (nextDate == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "下次校准日期不能为空");
        }
        status = status == null || status.isBlank() ? QcStatus.ENABLED : status;
    }

    /**
     * 校准完成后顺延计划。
     *
     * @param date 校准日期
     */
    public void advance(LocalDate date) {
        if (date == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "校准日期不能为空");
        }
        nextDate = date.plusMonths(cycleMonths);
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
