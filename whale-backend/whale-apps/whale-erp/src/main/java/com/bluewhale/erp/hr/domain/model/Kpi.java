package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 绩效指标。
 *
 * <p>不变量：名称非空；权重在 0–100 之间；目标值为非负。</p>
 *
 * @author BlueWhale
 */
@Data
public class Kpi {

    /** 权重上限 */
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("100");

    /** 主键 */
    private Long id;

    /** 绩效周期主键 */
    private Long cycleId;

    /** 指标名称 */
    private String name;

    /** 权重（百分比） */
    private BigDecimal weight;

    /** 目标值 */
    private BigDecimal targetValue;

    /** 计量单位 */
    private String unit;

    /** 备注 */
    private String remark;

    /**
     * 校验指标。
     */
    public void validate() {
        if (name == null || name.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "指标名称不能为空");
        }
        if (weight == null || weight.signum() < 0 || weight.compareTo(MAX_WEIGHT) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "指标权重必须在 0–100 之间");
        }
        if (targetValue != null && targetValue.signum() < 0) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "指标目标值不能为负数");
        }
    }
}
