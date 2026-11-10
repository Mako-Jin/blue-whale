package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 库存盘点单聚合。
 *
 * <p>不变量：账面数量在建档时快照；差异 = 实盘 - 账面；只有草稿可过账或取消；
 * 过账按差异生成出入库流水，盘盈借存货、盘亏贷存货。</p>
 *
 * @author BlueWhale
 */
@Data
public class Stocktake {

    /** 主键 */
    private Long id;

    /** 盘点单号 */
    private String docNo;

    /** 盘点日期 */
    private LocalDate stocktakeDate;

    /** 盘点仓库 */
    private Long warehouseId;

    /** 仓库名称快照 */
    private String warehouseName;

    /** 状态：DRAFT / POSTED / CANCELLED */
    private String status;

    /** 盘盈金额 */
    private BigDecimal gainAmount;

    /** 盘亏金额 */
    private BigDecimal lossAmount;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<StocktakeLine> lines;

    /**
     * 汇总盘盈/盘亏金额。
     */
    public void refreshTotals() {
        if (lines == null || lines.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID, "盘点单至少需要一条分录");
        }
        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        for (StocktakeLine line : lines) {
            BigDecimal amount = Amounts.nonNegative(line.getAmount() == null ? BigDecimal.ZERO : line.getAmount().abs(),
                    "盘点差异金额");
            if (line.getDifference() != null && line.getDifference().signum() > 0) {
                gain = gain.add(amount);
            } else if (line.getDifference() != null && line.getDifference().signum() < 0) {
                loss = loss.add(amount);
            }
        }
        gainAmount = gain;
        lossAmount = loss;
    }

    /**
     * 过账。
     */
    public void post() {
        requireDraft();
        status = ScmStatus.POSTED;
    }

    /**
     * 取消。
     */
    public void cancel() {
        requireDraft();
        status = ScmStatus.CANCELLED;
    }

    /**
     * 校验当前为草稿状态。
     */
    private void requireDraft() {
        if (!ScmStatus.DRAFT.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.STOCKTAKE_STATE_CONFLICT, "只有草稿盘点单可以过账或取消");
        }
    }
}
