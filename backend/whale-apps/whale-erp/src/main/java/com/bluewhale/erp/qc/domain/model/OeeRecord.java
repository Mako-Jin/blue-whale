package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 设备 OEE 记录聚合。
 *
 * <p>不变量：计划运行分钟为正；运行/停机/产量/合格量为非负；合格量不超过实际产量；
 * 各分项率与 OEE 上限为 1，OEE = 时间开动率 × 性能开动率 × 合格率（4 位小数）。
 * 按「设备编码 + 统计日期」唯一，MES 重复回传覆盖同一记录。</p>
 *
 * @author BlueWhale
 */
@Data
public class OeeRecord {

    /** 比率小数位 */
    private static final int RATE_SCALE = 4;

    /** 单件率上限 */
    private static final BigDecimal MAX_RATE = BigDecimal.ONE;

    /** 主键 */
    private Long id;

    /** 设备编码 */
    private String equipmentNo;

    /** 统计日期 */
    private LocalDate recordDate;

    /** 计划运行分钟 */
    private BigDecimal plannedMinutes;

    /** 实际运行分钟 */
    private BigDecimal runMinutes;

    /** 停机分钟 */
    private BigDecimal downMinutes;

    /** 理论产量 */
    private BigDecimal idealQty;

    /** 实际产量 */
    private BigDecimal outputQty;

    /** 合格产量 */
    private BigDecimal goodQty;

    /** 时间开动率 */
    private BigDecimal availabilityRate;

    /** 性能开动率 */
    private BigDecimal performanceRate;

    /** 合格率 */
    private BigDecimal qualityRate;

    /** OEE */
    private BigDecimal oeeRate;

    /**
     * 校验并计算 OEE 各分项率。
     */
    public void compute() {
        if (equipmentNo == null || equipmentNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "OEE 设备编码不能为空");
        }
        if (recordDate == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "OEE 统计日期不能为空");
        }
        if (plannedMinutes == null || plannedMinutes.signum() <= 0) {
            throw ErpErrorCode.exception(ErpErrorCode.OEE_PARAM_INVALID, "计划运行分钟必须大于 0");
        }
        runMinutes = Quantities.nonNegative(runMinutes, "实际运行分钟");
        downMinutes = Quantities.nonNegative(downMinutes, "停机分钟");
        outputQty = Quantities.nonNegative(outputQty, "实际产量");
        goodQty = Quantities.nonNegative(goodQty, "合格产量");
        if (idealQty == null || idealQty.signum() <= 0) {
            throw ErpErrorCode.exception(ErpErrorCode.OEE_PARAM_INVALID, "理论产量必须大于 0");
        }
        if (goodQty.compareTo(outputQty) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.OEE_PARAM_INVALID, "合格产量不能大于实际产量");
        }
        availabilityRate = ratio(runMinutes, plannedMinutes);
        performanceRate = ratio(outputQty, idealQty);
        qualityRate = outputQty.signum() == 0 ? BigDecimal.ZERO : ratio(goodQty, outputQty);
        oeeRate = availabilityRate.multiply(performanceRate).multiply(qualityRate)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 计算比率并封顶为 1。
     *
     * @param numerator 分子
     * @param denominator 分母
     * @return 比率
     */
    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal value = numerator.divide(denominator, RATE_SCALE, RoundingMode.HALF_UP);
        return value.compareTo(MAX_RATE) > 0 ? MAX_RATE : value;
    }
}
