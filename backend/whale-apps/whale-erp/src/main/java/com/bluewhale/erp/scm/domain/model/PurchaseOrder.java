package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 采购订单聚合根。
 *
 * <p>状态机：{@code DRAFT → APPROVED → PARTIALLY_RECEIVED → RECEIVED → CLOSED}；
 * {@code DRAFT/APPROVED} 可取消，已收货不可取消。</p>
 *
 * @author BlueWhale
 */
@Data
public class PurchaseOrder {

    /** 主键 */
    private Long id;

    /** 订单号 */
    private String docNo;

    /** 供应商主键 */
    private Long supplierId;

    /** 供应商名称快照 */
    private String supplierName;

    /** 单据日期 */
    private LocalDate docDate;

    /** 预计到货日期 */
    private LocalDate expectedDate;

    /** 默认收货仓库 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 已收货金额 */
    private BigDecimal receivedAmount;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    /**
     * 汇总分录金额并校验订单至少有一条分录。
     */
    public void calculateTotal() {
        if (lines == null || lines.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.VOUCHER_LINE_EMPTY, "采购订单至少需要一条分录");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderLine line : lines) {
            Quantities.requirePositive(line.getQuantity(), "订单数量");
            Amounts.requirePositive(line.getUnitPrice(), "单价");
            BigDecimal amount = line.getUnitPrice().multiply(line.getQuantity())
                    .setScale(Amounts.SCALE, java.math.RoundingMode.HALF_UP);
            line.setAmount(amount);
            total = total.add(amount);
        }
        this.totalAmount = total;
        this.receivedAmount = receivedAmount == null ? BigDecimal.ZERO : receivedAmount;
    }

    /**
     * 审核订单。
     */
    public void approve() {
        requireStatus(ScmStatus.DRAFT, "只有草稿状态可以审核");
        this.status = ScmStatus.APPROVED;
    }

    /**
     * 取消订单。
     */
    public void cancel() {
        if (ScmStatus.RECEIVED.equals(status) || ScmStatus.CLOSED.equals(status)
                || ScmStatus.CANCELLED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, "已收货或已关闭的订单不能取消");
        }
        this.status = ScmStatus.CANCELLED;
    }

    /**
     * 关闭订单（全部收货后人工关闭）。
     */
    public void close() {
        if (!ScmStatus.RECEIVED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, "只有全部收货的订单可以关闭");
        }
        this.status = ScmStatus.CLOSED;
    }

    /**
     * 收货：按行累加已收数量并刷新状态与已收金额。
     *
     * @param quantities 行号 → 本次收货数量
     */
    public void receive(Map<Integer, BigDecimal> quantities) {
        if (!ScmStatus.APPROVED.equals(status) && !ScmStatus.PARTIALLY_RECEIVED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, "只有已审核或部分收货的订单可以收货");
        }
        if (quantities == null || quantities.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID, "收货明细不能为空");
        }
        BigDecimal amount = BigDecimal.ZERO;
        for (Map.Entry<Integer, BigDecimal> entry : quantities.entrySet()) {
            PurchaseOrderLine line = findLine(entry.getKey());
            line.receive(entry.getValue());
            amount = amount.add(line.getUnitPrice().multiply(entry.getValue())
                    .setScale(Amounts.SCALE, java.math.RoundingMode.HALF_UP));
        }
        this.receivedAmount = (receivedAmount == null ? BigDecimal.ZERO : receivedAmount).add(amount);
        refreshStatus();
    }

    /**
     * 按行号查找分录。
     *
     * @param lineNo 行号
     * @return 分录
     */
    public PurchaseOrderLine findLine(Integer lineNo) {
        return lines.stream()
                .filter(line -> line.getLineNo().equals(lineNo))
                .findFirst()
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.VOUCHER_LINE_EMPTY,
                        "采购订单不存在第 " + lineNo + " 行"));
    }

    /**
     * 刷新收货状态。
     */
    private void refreshStatus() {
        boolean anyReceived = lines.stream()
                .anyMatch(line -> line.getReceivedQuantity() != null && line.getReceivedQuantity().signum() > 0);
        boolean allReceived = lines.stream().allMatch(PurchaseOrderLine::fullyReceived);
        if (allReceived) {
            this.status = ScmStatus.RECEIVED;
        } else if (anyReceived) {
            this.status = ScmStatus.PARTIALLY_RECEIVED;
        }
    }

    /**
     * 校验当前状态。
     *
     * @param expected 期望状态
     * @param message 错误提示
     */
    private void requireStatus(String expected, String message) {
        if (!expected.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, message);
        }
    }
}
