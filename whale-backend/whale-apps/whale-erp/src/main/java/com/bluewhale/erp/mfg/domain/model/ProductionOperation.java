package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产订单工序（PDM 路线工序快照 + 累计报工数量）。
 *
 * @author BlueWhale
 */
@Data
public class ProductionOperation {

    /** 工序序号 */
    private Integer stepNo;

    /** 工序名称 */
    private String name;

    /** 工作中心编码 */
    private String workCenterCode;

    /** 单件标准工时（小时） */
    private BigDecimal standardHours;

    /** 准备工时（小时） */
    private BigDecimal setupHours;

    /** 计划数量 */
    private BigDecimal plannedQuantity;

    /** 累计合格数量 */
    private BigDecimal qualifiedQuantity;

    /** 累计报废数量 */
    private BigDecimal scrappedQuantity;

    /** 状态：PENDING / IN_PROGRESS / COMPLETED */
    private String status;

    /** 首次报工时间 */
    private LocalDateTime firstReportAt;

    /** 最近报工时间 */
    private LocalDateTime lastReportAt;

    /**
     * 累计报工数量（合格 + 报废）。
     *
     * @return 累计报工数量
     */
    public BigDecimal reportedQuantity() {
        BigDecimal qualified = qualifiedQuantity == null ? BigDecimal.ZERO : qualifiedQuantity;
        BigDecimal scrapped = scrappedQuantity == null ? BigDecimal.ZERO : scrappedQuantity;
        return qualified.add(scrapped);
    }

    /**
     * 剩余待报工数量。
     *
     * @return 剩余数量，不为负
     */
    public BigDecimal remainingQuantity() {
        BigDecimal remaining = plannedQuantity.subtract(reportedQuantity());
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }

    /**
     * 是否已完工（累计报工达到计划量）。
     *
     * @return 已完工返回 true
     */
    public boolean completed() {
        return reportedQuantity().compareTo(plannedQuantity) >= 0;
    }

    /**
     * 报工：累加合格与报废数量，并推进状态。
     *
     * @param qualified 本次合格数量
     * @param scrapped  本次报废数量
     * @param reportedAt 报工时间
     */
    public void report(BigDecimal qualified, BigDecimal scrapped, LocalDateTime reportedAt) {
        BigDecimal qualifiedValue = nonNegative(qualified, "报工合格数量");
        BigDecimal scrappedValue = nonNegative(scrapped, "报工报废数量");
        if (qualifiedValue.add(scrappedValue).signum() <= 0) {
            throw ErpErrorCode.exception(ErpErrorCode.OPERATION_INVALID, "报工数量必须大于 0");
        }
        if (reportedQuantity().add(qualifiedValue).add(scrappedValue).compareTo(plannedQuantity) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.OPERATION_REPORT_EXCEEDED);
        }
        qualifiedQuantity = (qualifiedQuantity == null ? BigDecimal.ZERO : qualifiedQuantity).add(qualifiedValue);
        scrappedQuantity = (scrappedQuantity == null ? BigDecimal.ZERO : scrappedQuantity).add(scrappedValue);
        if (firstReportAt == null) {
            firstReportAt = reportedAt;
        }
        lastReportAt = reportedAt;
        status = completed() ? MfgStatus.COMPLETED : MfgStatus.IN_PROGRESS;
    }

    /**
     * 校验数量非负。
     *
     * @param value 数量
     * @param field 字段名
     * @return 原值或零
     */
    private BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return Quantities.nonNegative(value, field);
    }
}
