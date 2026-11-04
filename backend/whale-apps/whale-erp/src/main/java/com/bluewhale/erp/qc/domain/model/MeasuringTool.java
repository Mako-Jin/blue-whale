package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.time.LocalDate;

/**
 * 计量器具台账聚合。
 *
 * <p>不变量：量具编号租户内唯一；校准周期为正；校准过期或停用的量具不可用于检验；
 * 校准合格后回写上次校准日并按周期顺延下次校准日，不合格不改变日期（保持过期不可用）。</p>
 *
 * @author BlueWhale
 */
@Data
public class MeasuringTool {

    /** 主键 */
    private Long id;

    /** 量具编号 */
    private String toolNo;

    /** 量具名称 */
    private String name;

    /** 量具类型 */
    private String toolType;

    /** 精度值描述 */
    private String precisionValue;

    /** 校准周期（月） */
    private Integer calibrationCycleMonths;

    /** 上次校准日期 */
    private LocalDate lastCalibrationDate;

    /** 下次校准日期 */
    private LocalDate nextCalibrationDate;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 校验建档数据。
     */
    public void validateForCreate() {
        if (toolNo == null || toolNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "量具编号不能为空");
        }
        if (name == null || name.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "量具名称不能为空");
        }
        if (calibrationCycleMonths == null || calibrationCycleMonths <= 0) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "校准周期必须大于 0 个月");
        }
        if (nextCalibrationDate == null && lastCalibrationDate != null) {
            nextCalibrationDate = lastCalibrationDate.plusMonths(calibrationCycleMonths);
        }
        if (nextCalibrationDate == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "下次校准日期不能为空");
        }
        status = status == null || status.isBlank() ? QcStatus.ENABLED : status;
    }

    /**
     * 判断量具在指定日期是否可用于检验。
     *
     * @param date 检验日期
     * @return 启用且校准未过期返回 true
     */
    public boolean usableOn(LocalDate date) {
        return QcStatus.ENABLED.equals(status)
                && nextCalibrationDate != null
                && (date == null || !nextCalibrationDate.isBefore(date));
    }

    /**
     * 校准合格后回写校准日期。
     *
     * @param date 校准日期
     */
    public void calibrated(LocalDate date) {
        if (date == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "校准日期不能为空");
        }
        lastCalibrationDate = date;
        nextCalibrationDate = date.plusMonths(calibrationCycleMonths);
    }
}
