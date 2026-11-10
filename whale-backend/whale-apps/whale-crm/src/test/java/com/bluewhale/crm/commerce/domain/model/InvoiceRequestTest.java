package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link InvoiceRequest} 状态机单元测试。
 *
 * @author BlueWhale
 */
class InvoiceRequestTest {

    @Test
    @DisplayName("提交校验：明细合计不一致时拒绝")
    void shouldRejectItemTotalMismatch() {
        InvoiceRequest request = draftRequest(new BigDecimal("30.00"));
        InvoiceItem item = new InvoiceItem();
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("10"));
        request.getItems().add(item);

        BizException exception = assertThrows(BizException.class, request::validateForSubmit);

        assertEquals(CrmErrorCode.INVOICE_LINE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("提交校验：明细合计一致时按行号重排")
    void shouldRecalculateLineAmounts() {
        InvoiceRequest request = draftRequest(new BigDecimal("30.00"));
        request.getItems().add(item("3", "10"));

        request.validateForSubmit();

        assertEquals(1, request.getItems().get(0).getLineNo());
        assertEquals(new BigDecimal("30.00"), request.getItems().get(0).getAmount());
    }

    @Test
    @DisplayName("提交校验：金额不大于 0 时拒绝")
    void shouldRejectNonPositiveAmount() {
        InvoiceRequest request = draftRequest(BigDecimal.ZERO);

        BizException exception = assertThrows(BizException.class, request::validateForSubmit);

        assertEquals(CrmErrorCode.INVOICE_LINE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("提交校验：红冲单据不允许再次提交审批")
    void shouldRejectRedFlushSubmit() {
        InvoiceRequest request = draftRequest(new BigDecimal("30.00"));
        request.setRequestType(InvoiceRequestType.RED_FLUSH);

        BizException exception = assertThrows(BizException.class, request::validateForSubmit);

        assertEquals(CrmErrorCode.INVOICE_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("审批：待审批状态可流转通过或驳回，重复应用拒绝")
    void shouldApplyApprovalOnce() {
        InvoiceRequest request = draftRequest(new BigDecimal("30.00"));
        request.submit(88L, LocalDateTime.now());

        request.applyApproval(true, LocalDateTime.now());

        assertEquals(InvoiceStatus.APPROVED, request.getStatus());
        assertThrows(BizException.class, () -> request.applyApproval(false, LocalDateTime.now()));
    }

    @Test
    @DisplayName("开票：实际开票金额必须等于申请金额")
    void shouldRejectIssueAmountMismatch() {
        InvoiceRequest request = approvedRequest();

        BizException exception = assertThrows(BizException.class,
                () -> request.issue("CODE-1", "NO-1", new BigDecimal("29.00"), BigDecimal.ZERO,
                        LocalDateTime.now()));

        assertEquals(CrmErrorCode.INVOICE_LINE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("寄送：快递公司或运单号为空时拒绝")
    void shouldRejectSendWithoutTracking() {
        InvoiceRequest request = approvedRequest();
        request.issue("CODE-1", "NO-1", new BigDecimal("30.00"), BigDecimal.ZERO, LocalDateTime.now());

        BizException exception = assertThrows(BizException.class,
                () -> request.send("顺丰", " ", LocalDateTime.now()));

        assertEquals(CrmErrorCode.INVOICE_SEND_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("取消：仅草稿或已驳回允许取消，重复取消幂等")
    void shouldCancelIdempotent() {
        InvoiceRequest request = draftRequest(new BigDecimal("30.00"));

        request.cancel();
        request.cancel();

        assertEquals(InvoiceStatus.CANCELLED, request.getStatus());
    }

    @Test
    @DisplayName("红冲：仅已开票或已寄送的正向申请允许红冲")
    void shouldRequireRedFlushableStatus() {
        InvoiceRequest request = draftRequest(new BigDecimal("30.00"));

        BizException exception = assertThrows(BizException.class, request::requireRedFlushable);

        assertEquals(CrmErrorCode.INVOICE_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("红冲：原单标记红冲后落终态并记录原因")
    void shouldMarkRedFlushed() {
        InvoiceRequest request = approvedRequest();
        request.issue("CODE-1", "NO-1", new BigDecimal("30.00"), BigDecimal.ZERO, LocalDateTime.now());

        request.requireRedFlushable();
        request.markRedFlushed("客户退货", LocalDateTime.now());

        assertEquals(InvoiceStatus.RED_FLUSHED, request.getStatus());
        assertEquals("客户退货", request.getRedFlushReason());
    }

    /**
     * 构造草稿申请。
     *
     * @param amount 申请金额
     * @return 申请
     */
    private InvoiceRequest draftRequest(BigDecimal amount) {
        InvoiceRequest request = new InvoiceRequest();
        request.setId(1L);
        request.setInvoiceNo("INV-20261107-0001");
        request.setRequestType(InvoiceRequestType.NORMAL);
        request.setSourceType("CONTRACT");
        request.setSourceId(11L);
        request.setCustomerId(21L);
        request.setCustomerCode("CUS-001");
        request.setCurrency("CNY");
        request.setAmount(amount);
        request.setStatus(InvoiceStatus.DRAFT);
        return request;
    }

    /**
     * 构造审批通过申请。
     *
     * @return 申请
     */
    private InvoiceRequest approvedRequest() {
        InvoiceRequest request = draftRequest(new BigDecimal("30.00"));
        request.setStatus(InvoiceStatus.APPROVED);
        return request;
    }

    /**
     * 构造申请行。
     *
     * @param quantity 数量
     * @param price 单价
     * @return 申请行
     */
    private InvoiceItem item(String quantity, String price) {
        InvoiceItem item = new InvoiceItem();
        item.setItemName("服务费");
        item.setQuantity(new BigDecimal(quantity));
        item.setUnitPrice(new BigDecimal(price));
        return item;
    }
}
