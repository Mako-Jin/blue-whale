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
 * 销售退货单聚合根（红字入库）。
 *
 * <p>状态机：{@code DRAFT → POSTED}；{@code DRAFT} 可取消；过账后不可撤销。</p>
 *
 * @author BlueWhale
 */
@Data
public class SalesReturn {

    /** 主键 */
    private Long id;

    /** 退货单号 */
    private String docNo;

    /** 源销售订单主键 */
    private Long salesOrderId;

    /** 客户主键 */
    private Long customerId;

    /** 客户名称快照 */
    private String customerName;

    /** 退货日期 */
    private LocalDate docDate;

    /** 退货入库仓库 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 退货总额 */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;

    /** CRM 退换货申请单号（幂等键） */
    private String crmReturnNo;

    /** 来源系统类型 */
    private String sourceType;

    /** 来源单号 */
    private String sourceId;

    /** 分录 */
    private List<SalesReturnLine> lines = new ArrayList<>();

    /**
     * 汇总分录金额并校验至少一条分录。
     */
    public void calculateTotal() {
        if (lines == null || lines.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.RETURN_INVALID, "退货单至少需要一条分录");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SalesReturnLine line : lines) {
            Quantities.requirePositive(line.getQuantity(), "退货数量");
            Amounts.requirePositive(line.getUnitPrice(), "退货单价");
            line.calculateAmount();
            total = total.add(line.getAmount());
        }
        this.totalAmount = total;
    }

    /**
     * 过账：仅草稿状态可过账。
     */
    public void post() {
        requireDraft("只有草稿状态的销售退货单可以过账");
        this.status = ScmStatus.POSTED;
    }

    /**
     * 取消：仅草稿状态可取消。
     */
    public void cancel() {
        requireDraft("只有草稿状态的销售退货单可以取消");
        this.status = ScmStatus.CANCELLED;
    }

    /**
     * 计算行金额（单价 × 数量，保留 2 位）。
     *
     * @param unitPrice 单价
     * @param quantity 数量
     * @return 行金额
     */
    static BigDecimal amountOf(BigDecimal unitPrice, BigDecimal quantity) {
        return unitPrice.multiply(quantity).setScale(Amounts.SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 校验草稿状态。
     *
     * @param message 错误提示
     */
    private void requireDraft(String message) {
        if (!ScmStatus.DRAFT.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.RETURN_STATE_CONFLICT, message);
        }
    }
}
