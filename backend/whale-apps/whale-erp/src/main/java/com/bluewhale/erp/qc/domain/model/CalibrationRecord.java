package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.time.LocalDate;

/**
 * 量具校准记录聚合。
 *
 * <p>不变量：量具编号与校准日期必填；结论只能为 PASS 或 FAIL。</p>
 *
 * @author BlueWhale
 */
@Data
public class CalibrationRecord {

    /** 主键 */
    private Long id;

    /** 校准记录号 */
    private String recordNo;

    /** 量具编号 */
    private String toolNo;

    /** 校准计划主键 */
    private Long planId;

    /** 校准日期 */
    private LocalDate calibrationDate;

    /** 校准结论：PASS / FAIL */
    private String result;

    /** 校准证书号 */
    private String certificateNo;

    /** 备注 */
    private String remark;

    /**
     * 校验建档数据。
     */
    public void validateForCreate() {
        if (toolNo == null || toolNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "校准记录量具编号不能为空");
        }
        if (calibrationDate == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "校准日期不能为空");
        }
        if (!QcStatus.RESULT_PASS.equals(result) && !QcStatus.RESULT_FAIL.equals(result)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "校准结论只能为 PASS 或 FAIL");
        }
    }

    /**
     * 判断校准是否合格。
     *
     * @return 合格返回 true
     */
    public boolean passed() {
        return QcStatus.RESULT_PASS.equals(result);
    }
}
