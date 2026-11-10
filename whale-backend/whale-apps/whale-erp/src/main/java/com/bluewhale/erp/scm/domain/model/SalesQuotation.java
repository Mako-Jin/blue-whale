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

/**
 * 销售报价单聚合根。
 *
 * <p>状态机：{@code DRAFT → ACCEPTED → CONVERTED}；{@code CONVERTED} 不可取消，
 * 其余状态取消后置 {@code CANCELLED}。</p>
 *
 * @author BlueWhale
 */
@Data
public class SalesQuotation {

    /** 主键 */
    private Long id;

    /** 报价单号 */
    private String docNo;

    /** 客户主键 */
    private Long customerId;

    /** 客户名称快照 */
    private String customerName;

    /** 报价日期 */
    private LocalDate docDate;

    /** 报价有效期至 */
    private LocalDate validUntil;

    /** 发货仓库 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 报价总额 */
    private BigDecimal totalAmount;

    /** 转单生成的销售订单号 */
    private String convertedOrderNo;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<SalesQuotationLine> lines = new ArrayList<>();

    /**
     * 汇总分录金额并校验至少一条分录。
     */
    public void calculateTotal() {
        if (lines == null || lines.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.VOUCHER_LINE_EMPTY, "销售报价单至少需要一条分录");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SalesQuotationLine line : lines) {
            Quantities.requirePositive(line.getQuantity(), "报价数量");
            Amounts.requirePositive(line.getUnitPrice(), "报价单价");
            line.calculateAmount();
            total = total.add(line.getAmount());
        }
        this.totalAmount = total;
    }

    /**
     * 客户接受报价：仅草稿状态可接受。
     */
    public void accept() {
        if (!ScmStatus.DRAFT.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, "只有草稿状态的销售报价单可以接受");
        }
        this.status = ScmStatus.ACCEPTED;
    }

    /**
     * 转单：仅客户已接受状态可转单，回写生成的订单号并置已转单。
     *
     * @param orderNo 生成的销售订单号
     */
    public void convert(String orderNo) {
        if (!ScmStatus.ACCEPTED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, "只有客户已接受的销售报价单可以转单");
        }
        this.convertedOrderNo = orderNo;
        this.status = ScmStatus.CONVERTED;
    }

    /**
     * 取消：已转单的报价单拒绝取消。
     */
    public void cancel() {
        if (ScmStatus.CONVERTED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, "已转单的销售报价单不能取消");
        }
        this.status = ScmStatus.CANCELLED;
    }

    /**
     * 计算行金额（报价单价 × 数量，保留 2 位）。
     *
     * @param unitPrice 报价单价
     * @param quantity 数量
     * @return 行金额
     */
    static BigDecimal amountOf(BigDecimal unitPrice, BigDecimal quantity) {
        return unitPrice.multiply(quantity).setScale(Amounts.SCALE, RoundingMode.HALF_UP);
    }
}
