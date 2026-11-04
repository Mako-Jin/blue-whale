package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 主生产计划聚合。
 *
 * <p>不变量：计划至少一条分录；计划数量为正；只有草稿可下达或取消。</p>
 *
 * @author BlueWhale
 */
@Data
public class MpsPlan {

    /** 主键 */
    private Long id;

    /** 计划单号 */
    private String planNo;

    /** 计划期间（YYYY-MM） */
    private String period;

    /** 状态：DRAFT / RELEASED / CANCELLED */
    private String status;

    /** 计划总数量 */
    private BigDecimal totalPlanned;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<MpsPlanLine> lines;

    /**
     * 校验并汇总计划数量。
     */
    public void calculateTotal() {
        if (lines == null || lines.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID, "主生产计划至少需要一条分录");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (MpsPlanLine line : lines) {
            line.validate();
            total = total.add(line.getPlannedQuantity());
        }
        totalPlanned = total;
    }

    /**
     * 下达计划：分录已下达数量取计划数量。
     */
    public void release() {
        requireDraft();
        for (MpsPlanLine line : lines) {
            line.release();
        }
        status = MfgStatus.RELEASED;
    }

    /**
     * 取消计划。
     */
    public void cancel() {
        requireDraft();
        status = MfgStatus.CANCELLED;
    }

    /**
     * 校验当前为草稿状态。
     */
    private void requireDraft() {
        if (!MfgStatus.DRAFT.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_STATE_CONFLICT, "只有草稿主生产计划可以下达或取消");
        }
    }
}
