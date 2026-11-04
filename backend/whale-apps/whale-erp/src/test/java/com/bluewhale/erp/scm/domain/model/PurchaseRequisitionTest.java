package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link PurchaseRequisition} 领域行为单元测试。
 *
 * @author BlueWhale
 */
class PurchaseRequisitionTest {

    @Test
    @DisplayName("计算合计：行金额 2 位 HALF_UP，头金额为行合计")
    void shouldCalculateTotal() {
        PurchaseRequisition requisition = new PurchaseRequisition();
        requisition.setLines(List.of(line(1, "1.5", "3.01"), line(2, "2", "10")));

        requisition.calculateTotal();

        assertEquals(new BigDecimal("4.52"), requisition.getLines().get(0).getAmount());
        assertEquals(new BigDecimal("20.00"), requisition.getLines().get(1).getAmount());
        assertEquals(0, requisition.getTotalAmount().compareTo(new BigDecimal("24.52")));
    }

    @Test
    @DisplayName("计算合计：无分录拒绝")
    void shouldRejectEmptyLines() {
        PurchaseRequisition requisition = new PurchaseRequisition();

        BizException exception = assertThrows(BizException.class, requisition::calculateTotal);

        assertEquals(ErpErrorCode.VOUCHER_LINE_EMPTY, exception.getCode());
    }

    @Test
    @DisplayName("计算合计：数量非正拒绝")
    void shouldRejectNonPositiveQuantity() {
        PurchaseRequisition requisition = new PurchaseRequisition();
        requisition.setLines(List.of(line(1, "0", "10")));

        BizException exception = assertThrows(BizException.class, requisition::calculateTotal);

        assertEquals(ErpErrorCode.QUANTITY_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("计算合计：单价非正拒绝")
    void shouldRejectNonPositivePrice() {
        PurchaseRequisition requisition = new PurchaseRequisition();
        requisition.setLines(List.of(line(1, "1", "0")));

        BizException exception = assertThrows(BizException.class, requisition::calculateTotal);

        assertEquals(ErpErrorCode.AMOUNT_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("审核：草稿可审核")
    void shouldApproveDraft() {
        PurchaseRequisition requisition = draft();

        requisition.approve();

        assertEquals(ScmStatus.APPROVED, requisition.getStatus());
    }

    @Test
    @DisplayName("审核：非草稿拒绝")
    void shouldRejectApproveNonDraft() {
        PurchaseRequisition requisition = draft();
        requisition.setStatus(ScmStatus.APPROVED);

        BizException exception = assertThrows(BizException.class, requisition::approve);

        assertEquals(ErpErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("转单：已审核可转单并回写订单号")
    void shouldConvertApproved() {
        PurchaseRequisition requisition = draft();
        requisition.setStatus(ScmStatus.APPROVED);

        requisition.convert("PO-001");

        assertEquals(ScmStatus.CONVERTED, requisition.getStatus());
        assertEquals("PO-001", requisition.getConvertedOrderNo());
    }

    @Test
    @DisplayName("转单：重复转单拒绝")
    void shouldRejectDuplicateConvert() {
        PurchaseRequisition requisition = draft();
        requisition.setStatus(ScmStatus.CONVERTED);

        BizException exception = assertThrows(BizException.class, () -> requisition.convert("PO-002"));

        assertEquals(ErpErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("取消：草稿可取消")
    void shouldCancelDraft() {
        PurchaseRequisition requisition = draft();

        requisition.cancel();

        assertEquals(ScmStatus.CANCELLED, requisition.getStatus());
    }

    @Test
    @DisplayName("取消：已转单拒绝")
    void shouldRejectCancelConverted() {
        PurchaseRequisition requisition = draft();
        requisition.setStatus(ScmStatus.CONVERTED);

        BizException exception = assertThrows(BizException.class, requisition::cancel);

        assertEquals(ErpErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造草稿申请单。
     *
     * @return 申请单
     */
    private PurchaseRequisition draft() {
        PurchaseRequisition requisition = new PurchaseRequisition();
        requisition.setId(1L);
        requisition.setDocNo("PA-001");
        requisition.setStatus(ScmStatus.DRAFT);
        requisition.setLines(new ArrayList<>(List.of(line(1, "2", "10"))));
        requisition.calculateTotal();
        return requisition;
    }

    /**
     * 构造分录。
     *
     * @param lineNo 行号
     * @param quantity 数量
     * @param unitPrice 单价
     * @return 分录
     */
    private PurchaseRequisitionLine line(int lineNo, String quantity, String unitPrice) {
        PurchaseRequisitionLine line = new PurchaseRequisitionLine();
        line.setLineNo(lineNo);
        line.setItemId(1L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setUom("个");
        line.setQuantity(new BigDecimal(quantity));
        line.setUnitPrice(new BigDecimal(unitPrice));
        return line;
    }
}
