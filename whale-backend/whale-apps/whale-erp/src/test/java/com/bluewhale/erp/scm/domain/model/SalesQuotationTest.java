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
 * {@link SalesQuotation} 领域行为单元测试。
 *
 * @author BlueWhale
 */
class SalesQuotationTest {

    @Test
    @DisplayName("计算合计：行金额 2 位 HALF_UP，头金额为行合计")
    void shouldCalculateTotal() {
        SalesQuotation quotation = new SalesQuotation();
        quotation.setLines(List.of(line(1, "1.5", "3.01"), line(2, "2", "10")));

        quotation.calculateTotal();

        assertEquals(new BigDecimal("4.52"), quotation.getLines().get(0).getAmount());
        assertEquals(new BigDecimal("20.00"), quotation.getLines().get(1).getAmount());
        assertEquals(0, quotation.getTotalAmount().compareTo(new BigDecimal("24.52")));
    }

    @Test
    @DisplayName("计算合计：无分录拒绝")
    void shouldRejectEmptyLines() {
        SalesQuotation quotation = new SalesQuotation();

        BizException exception = assertThrows(BizException.class, quotation::calculateTotal);

        assertEquals(ErpErrorCode.VOUCHER_LINE_EMPTY, exception.getCode());
    }

    @Test
    @DisplayName("计算合计：数量非正拒绝")
    void shouldRejectNonPositiveQuantity() {
        SalesQuotation quotation = new SalesQuotation();
        quotation.setLines(List.of(line(1, "0", "10")));

        BizException exception = assertThrows(BizException.class, quotation::calculateTotal);

        assertEquals(ErpErrorCode.QUANTITY_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("计算合计：单价非正拒绝")
    void shouldRejectNonPositivePrice() {
        SalesQuotation quotation = new SalesQuotation();
        quotation.setLines(List.of(line(1, "1", "0")));

        BizException exception = assertThrows(BizException.class, quotation::calculateTotal);

        assertEquals(ErpErrorCode.AMOUNT_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("接受：草稿可接受")
    void shouldAcceptDraft() {
        SalesQuotation quotation = draft();

        quotation.accept();

        assertEquals(ScmStatus.ACCEPTED, quotation.getStatus());
    }

    @Test
    @DisplayName("接受：非草稿拒绝")
    void shouldRejectAcceptNonDraft() {
        SalesQuotation quotation = draft();
        quotation.setStatus(ScmStatus.ACCEPTED);

        BizException exception = assertThrows(BizException.class, quotation::accept);

        assertEquals(ErpErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("转单：客户已接受可转单并回写订单号")
    void shouldConvertAccepted() {
        SalesQuotation quotation = draft();
        quotation.setStatus(ScmStatus.ACCEPTED);

        quotation.convert("SO-001");

        assertEquals(ScmStatus.CONVERTED, quotation.getStatus());
        assertEquals("SO-001", quotation.getConvertedOrderNo());
    }

    @Test
    @DisplayName("转单：未接受或重复转单拒绝")
    void shouldRejectConvertWithoutAccept() {
        SalesQuotation quotation = draft();

        BizException exception = assertThrows(BizException.class, () -> quotation.convert("SO-002"));

        assertEquals(ErpErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("取消：草稿可取消")
    void shouldCancelDraft() {
        SalesQuotation quotation = draft();

        quotation.cancel();

        assertEquals(ScmStatus.CANCELLED, quotation.getStatus());
    }

    @Test
    @DisplayName("取消：已转单拒绝")
    void shouldRejectCancelConverted() {
        SalesQuotation quotation = draft();
        quotation.setStatus(ScmStatus.CONVERTED);

        BizException exception = assertThrows(BizException.class, quotation::cancel);

        assertEquals(ErpErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造草稿报价单。
     *
     * @return 报价单
     */
    private SalesQuotation draft() {
        SalesQuotation quotation = new SalesQuotation();
        quotation.setId(1L);
        quotation.setDocNo("SQ-001");
        quotation.setStatus(ScmStatus.DRAFT);
        quotation.setLines(new ArrayList<>(List.of(line(1, "2", "10"))));
        quotation.calculateTotal();
        return quotation;
    }

    /**
     * 构造分录。
     *
     * @param lineNo 行号
     * @param quantity 数量
     * @param unitPrice 单价
     * @return 分录
     */
    private SalesQuotationLine line(int lineNo, String quantity, String unitPrice) {
        SalesQuotationLine line = new SalesQuotationLine();
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
