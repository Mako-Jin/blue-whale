package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

/**
 * 检验项。
 *
 * <p>不变量：名称非空；结论只能为 {@code PASS} 或 {@code FAIL}。</p>
 *
 * @author BlueWhale
 */
@Data
public class InspectionItem {

    /** 行号 */
    private Integer lineNo;

    /** 检验项名称 */
    private String itemName;

    /** 标准值/规格 */
    private String standardValue;

    /** 实测值 */
    private String actualValue;

    /** 结论：PASS / FAIL */
    private String result;

    /** 备注 */
    private String remark;

    /**
     * 校验检验项定义。
     */
    public void validate() {
        if (itemName == null || itemName.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "检验项名称不能为空");
        }
        if (result != null && !QcStatus.RESULT_PASS.equals(result) && !QcStatus.RESULT_FAIL.equals(result)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "检验项结论只能为 PASS 或 FAIL");
        }
    }

    /**
     * 录入实测值与结论。
     *
     * @param actual 实测值
     * @param conclusion 结论：PASS / FAIL
     * @param note 备注
     */
    public void record(String actual, String conclusion, String note) {
        if (conclusion == null || conclusion.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "检验项结论不能为空");
        }
        if (!QcStatus.RESULT_PASS.equals(conclusion) && !QcStatus.RESULT_FAIL.equals(conclusion)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "检验项结论只能为 PASS 或 FAIL");
        }
        actualValue = actual;
        result = conclusion;
        if (note != null) {
            remark = note;
        }
    }

    /**
     * 判断检验项是否已录入结论。
     *
     * @return 已录入返回 true
     */
    public boolean completed() {
        return result != null && !result.isBlank();
    }

    /**
     * 判断检验项是否合格。
     *
     * @return 合格返回 true
     */
    public boolean passed() {
        return QcStatus.RESULT_PASS.equals(result);
    }
}
