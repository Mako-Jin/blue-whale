package com.bluewhale.crm.service.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReturnRequest} 状态机单元测试。
 *
 * @author BlueWhale
 */
class ReturnRequestTest {

    @Test
    @DisplayName("提交：草稿转审批中并记录流程实例")
    void shouldSubmitToPendingApproval() {
        ReturnRequest request = draftRequest();

        request.submit(88L);

        assertEquals(ReturnStatus.PENDING_APPROVAL, request.getStatus());
        assertEquals(88L, request.getApprovalInstanceId());
    }

    @Test
    @DisplayName("提交：非草稿或已驳回状态拒绝")
    void shouldRejectSubmitInWrongStatus() {
        ReturnRequest request = draftRequest();
        request.setStatus(ReturnStatus.APPROVED);

        BizException exception = assertThrows(BizException.class, () -> request.submit(88L));

        assertEquals(CrmErrorCode.RETURN_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("审批：通过转已通过、驳回转已驳回")
    void shouldApplyApprovalResult() {
        ReturnRequest approved = draftRequest();
        approved.setStatus(ReturnStatus.PENDING_APPROVAL);
        approved.applyApproval(true);
        assertEquals(ReturnStatus.APPROVED, approved.getStatus());

        ReturnRequest rejected = draftRequest();
        rejected.setStatus(ReturnStatus.PENDING_APPROVAL);
        rejected.applyApproval(false);
        assertEquals(ReturnStatus.REJECTED, rejected.getStatus());
    }

    @Test
    @DisplayName("审批：非审批中状态拒绝应用结果")
    void shouldRejectApprovalInWrongStatus() {
        ReturnRequest request = draftRequest();

        BizException exception = assertThrows(BizException.class, () -> request.applyApproval(true));

        assertEquals(CrmErrorCode.RETURN_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("执行：累计下发次数并清理失败原因")
    void shouldBeginExecute() {
        ReturnRequest request = draftRequest();
        request.setStatus(ReturnStatus.APPROVED);
        request.setPushAttempts(1);
        request.setLastError("timeout");

        request.beginExecute();

        assertEquals(ReturnStatus.EXECUTING, request.getStatus());
        assertEquals(2, request.getPushAttempts());
        assertNull(request.getLastError());
    }

    @Test
    @DisplayName("执行：草稿状态拒绝下发")
    void shouldRejectExecuteInDraft() {
        ReturnRequest request = draftRequest();

        BizException exception = assertThrows(BizException.class, request::beginExecute);

        assertEquals(CrmErrorCode.RETURN_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("执行失败：转失败并记录原因，可重试")
    void shouldMarkFailedAndAllowRetry() {
        ReturnRequest request = draftRequest();
        request.setStatus(ReturnStatus.EXECUTING);

        request.markFailed("erp down");

        assertEquals(ReturnStatus.FAILED, request.getStatus());
        assertEquals("erp down", request.getLastError());
        assertTrue(request.isExecutable());
        assertTrue(request.isApproved());
    }

    @Test
    @DisplayName("完成：回写 ERP 退货单号并清理失败原因")
    void shouldMarkCompleted() {
        ReturnRequest request = draftRequest();
        request.setStatus(ReturnStatus.EXECUTING);
        request.setLastError("timeout");

        request.markCompleted("CRM-SR-RET-1", "POSTED");

        assertEquals(ReturnStatus.COMPLETED, request.getStatus());
        assertEquals("CRM-SR-RET-1", request.getErpReturnNo());
        assertEquals("POSTED", request.getErpStatus());
        assertNull(request.getLastError());
        assertTrue(request.isFinished());
    }

    @Test
    @DisplayName("取消：草稿可取消，已通过拒绝取消")
    void shouldCancelOnlyEditableStatus() {
        ReturnRequest draft = draftRequest();
        draft.cancel();
        assertEquals(ReturnStatus.CANCELLED, draft.getStatus());

        ReturnRequest approved = draftRequest();
        approved.setStatus(ReturnStatus.APPROVED);
        BizException exception = assertThrows(BizException.class, approved::cancel);

        assertEquals(CrmErrorCode.RETURN_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("可执行判断：草稿与已完成均不可下发")
    void shouldNotBeExecutableWhenDraftOrCompleted() {
        ReturnRequest request = draftRequest();
        assertFalse(request.isExecutable());

        request.setStatus(ReturnStatus.COMPLETED);
        assertFalse(request.isExecutable());
    }

    @Test
    @DisplayName("行金额：单价乘数量四舍五入两位")
    void shouldCalculateItemAmount() {
        ReturnRequestItem item = new ReturnRequestItem();
        item.setQuantity(new BigDecimal("3.333"));
        item.setUnitPrice(new BigDecimal("2"));

        assertEquals(new BigDecimal("6.67"), item.calculateAmount());
    }

    /**
     * 构造草稿申请。
     *
     * @return 申请
     */
    private ReturnRequest draftRequest() {
        ReturnRequest request = new ReturnRequest();
        request.setId(1L);
        request.setReturnNo("RET-20261107-0001");
        request.setRequestType("RETURN");
        request.setOrderId(10L);
        request.setErpOrderNo("ERP-SO-1");
        request.setCustomerId(21L);
        request.setStatus(ReturnStatus.DRAFT);
        request.setPushAttempts(0);
        request.setLockVersion(0);
        return request;
    }
}
