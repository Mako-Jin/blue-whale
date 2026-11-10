package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 需求预测批次聚合。
 *
 * <p>不变量：批次至少一条分录；分录数量为正；只有草稿可发布；
 * 草稿或已发布可取消。</p>
 *
 * @author BlueWhale
 */
@Data
public class Forecast {

    /** 主键 */
    private Long id;

    /** 预测批次号 */
    private String batchNo;

    /** 预测期间（YYYY-MM） */
    private String period;

    /** 版本号 */
    private Integer version;

    /** 状态：DRAFT / PUBLISHED / CANCELLED */
    private String status;

    /** 预测总数量 */
    private BigDecimal totalQuantity;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<ForecastLine> lines;

    /**
     * 校验并汇总预测数量。
     */
    public void calculateTotal() {
        if (lines == null || lines.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID, "预测批次至少需要一条分录");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ForecastLine line : lines) {
            line.validate();
            total = total.add(line.getQuantity());
        }
        totalQuantity = total;
    }

    /**
     * 发布预测。
     */
    public void publish() {
        if (!MfgStatus.DRAFT.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_STATE_CONFLICT, "只有草稿预测可以发布");
        }
        status = MfgStatus.PUBLISHED;
    }

    /**
     * 取消预测。
     */
    public void cancel() {
        if (!MfgStatus.DRAFT.equals(status) && !MfgStatus.PUBLISHED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_STATE_CONFLICT, "已取消的预测不能再次取消");
        }
        status = MfgStatus.CANCELLED;
    }
}
