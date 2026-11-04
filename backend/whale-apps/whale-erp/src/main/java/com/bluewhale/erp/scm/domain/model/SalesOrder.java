package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 销售订单聚合根。
 *
 * <p>状态机：{@code DRAFT → APPROVED → PARTIALLY_SHIPPED → SHIPPED → CLOSED}；
 * {@code DRAFT/APPROVED} 可取消，已发货不可取消。</p>
 *
 * @author BlueWhale
 */
@Data
public class SalesOrder {

    /** 主键 */
    private Long id;

    /** 订单号 */
    private String docNo;

    /** 客户主键 */
    private Long customerId;

    /** 客户名称快照 */
    private String customerName;

    /** 单据日期 */
    private LocalDate docDate;

    /** 计划发货日期 */
    private LocalDate deliveryDate;

    /** 默认发货仓库 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 已发货金额 */
    private BigDecimal shippedAmount;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<SalesOrderLine> lines = new ArrayList<>();

    /**
     * 汇总分录金额并校验订单至少有一条分录。
     */
    public void calculateTotal() {
        if (lines == null || lines.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.VOUCHER_LINE_EMPTY, "销售订单至少需要一条分录");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderLine line : lines) {
            Quantities.requirePositive(line.getQuantity(), "订单数量");
            Amounts.requirePositive(line.getUnitPrice(), "单价");
            BigDecimal amount = line.getUnitPrice().multiply(line.getQuantity())
                    .setScale(Amounts.SCALE, RoundingMode.HALF_UP);
            line.setAmount(amount);
            total = total.add(amount);
        }
        this.totalAmount = total;
        this.shippedAmount = shippedAmount == null ? BigDecimal.ZERO : shippedAmount;
    }

    /**
     * 审核订单。
     */
    public void approve() {
        requireDraft("只有草稿状态可以审核");
        this.status = ScmStatus.APPROVED;
    }

    /**
     * 取消订单。
     */
    public void cancel() {
        if (ScmStatus.SHIPPED.equals(status) || ScmStatus.CLOSED.equals(status)
                || ScmStatus.CANCELLED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, "已发货或已关闭的订单不能取消");
        }
        this.status = ScmStatus.CANCELLED;
    }

    /**
     * 关闭订单（全部发货后人工关闭）。
     */
    public void close() {
        if (!ScmStatus.SHIPPED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, "只有全部发货的订单可以关闭");
        }
        this.status = ScmStatus.CLOSED;
    }

    /**
     * 发货：按行累加已发数量并刷新状态与已发金额。
     *
     * @param quantities 行号 → 本次发货数量
     */
    public void ship(Map<Integer, BigDecimal> quantities) {
        if (!ScmStatus.APPROVED.equals(status) && !ScmStatus.PARTIALLY_SHIPPED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, "只有已审核或部分发货的订单可以发货");
        }
        if (quantities == null || quantities.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID, "发货明细不能为空");
        }
        BigDecimal amount = BigDecimal.ZERO;
        for (Map.Entry<Integer, BigDecimal> entry : quantities.entrySet()) {
            SalesOrderLine line = findLine(entry.getKey());
            line.ship(entry.getValue());
            amount = amount.add(line.getUnitPrice().multiply(entry.getValue())
                    .setScale(Amounts.SCALE, RoundingMode.HALF_UP));
        }
        this.shippedAmount = (shippedAmount == null ? BigDecimal.ZERO : shippedAmount).add(amount);
        refreshStatus();
    }

    /**
     * 按行号查找分录。
     *
     * @param lineNo 行号
     * @return 分录
     */
    public SalesOrderLine findLine(Integer lineNo) {
        return lines.stream()
                .filter(line -> line.getLineNo().equals(lineNo))
                .findFirst()
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.VOUCHER_LINE_EMPTY,
                        "销售订单不存在第 " + lineNo + " 行"));
    }

    /**
     * 刷新发货状态。
     */
    private void refreshStatus() {
        boolean anyShipped = lines.stream()
                .anyMatch(line -> line.getShippedQuantity() != null && line.getShippedQuantity().signum() > 0);
        boolean allShipped = lines.stream().allMatch(SalesOrderLine::fullyShipped);
        if (allShipped) {
            this.status = ScmStatus.SHIPPED;
        } else if (anyShipped) {
            this.status = ScmStatus.PARTIALLY_SHIPPED;
        }
    }

    /**
     * 校验草稿状态。
     *
     * @param message 错误提示
     */
    private void requireDraft(String message) {
        if (!ScmStatus.DRAFT.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, message);
        }
    }
}
