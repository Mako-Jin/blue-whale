package com.bluewhale.crm.sales.application.view;

import com.bluewhale.crm.sales.domain.model.ForecastAdjustment;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预测调整记录视图。
 *
 * @author BlueWhale
 */
@Data
public class ForecastAdjustmentView {

    /** 记录 ID */
    private Long id;

    /** 快照 ID */
    private Long snapshotId;

    /** 调整字段 */
    private String fieldCode;

    /** 调整前金额 */
    private BigDecimal beforeAmount;

    /** 调整后金额 */
    private BigDecimal afterAmount;

    /** 调整原因 */
    private String reason;

    /** 操作人 */
    private Long operatorId;

    /** 调整时间 */
    private LocalDateTime adjustedAt;

    /**
     * 领域模型转视图。
     *
     * @param adjustment 调整记录
     * @return 视图
     */
    public static ForecastAdjustmentView from(ForecastAdjustment adjustment) {
        ForecastAdjustmentView view = new ForecastAdjustmentView();
        view.setId(adjustment.getId());
        view.setSnapshotId(adjustment.getSnapshotId());
        view.setFieldCode(adjustment.getFieldCode());
        view.setBeforeAmount(adjustment.getBeforeAmount());
        view.setAfterAmount(adjustment.getAfterAmount());
        view.setReason(adjustment.getReason());
        view.setOperatorId(adjustment.getOperatorId());
        view.setAdjustedAt(adjustment.getAdjustedAt());
        return view;
    }
}
