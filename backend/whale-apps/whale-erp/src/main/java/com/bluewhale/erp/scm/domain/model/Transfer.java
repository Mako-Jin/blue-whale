package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 库存调拨单聚合。
 *
 * <p>不变量：调出与调入仓库必须不同；分录数量为正；只有草稿可过账或取消；
 * 过账后调出仓出库、调入仓按调出仓移动加权成本入库。</p>
 *
 * @author BlueWhale
 */
@Data
public class Transfer {

    /** 主键 */
    private Long id;

    /** 调拨单号 */
    private String docNo;

    /** 调拨日期 */
    private LocalDate transferDate;

    /** 调出仓库 */
    private Long fromWarehouseId;

    /** 调出仓库名称快照 */
    private String fromWarehouseName;

    /** 调入仓库 */
    private Long toWarehouseId;

    /** 调入仓库名称快照 */
    private String toWarehouseName;

    /** 状态：DRAFT / POSTED / CANCELLED */
    private String status;

    /** 调拨总数量 */
    private BigDecimal totalQuantity;

    /** 调拨总金额 */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<TransferLine> lines;

    /**
     * 校验并汇总数量与金额。
     */
    public void calculateTotal() {
        if (lines == null || lines.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID, "调拨单至少需要一条分录");
        }
        if (fromWarehouseId == null || toWarehouseId == null || fromWarehouseId.equals(toWarehouseId)) {
            throw ErpErrorCode.exception(ErpErrorCode.TRANSFER_INVALID);
        }
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        for (TransferLine line : lines) {
            line.validate();
            quantity = quantity.add(line.getQuantity());
            amount = amount.add(Amounts.nonNegative(line.getAmount(), "调拨金额"));
        }
        totalQuantity = quantity;
        totalAmount = amount;
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
            throw ErpErrorCode.exception(ErpErrorCode.TRANSFER_STATE_CONFLICT, "只有草稿调拨单可以过账或取消");
        }
    }
}
