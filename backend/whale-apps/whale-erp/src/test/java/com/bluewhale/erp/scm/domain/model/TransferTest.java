package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Transfer} 领域规则测试。
 *
 * @author BlueWhale
 */
class TransferTest {

    @Test
    @DisplayName("汇总调拨数量并允许金额为零")
    void shouldCalculateTotal() {
        Transfer transfer = draftTransfer();

        transfer.calculateTotal();

        assertEquals(0, transfer.getTotalQuantity().compareTo(new BigDecimal("5")));
        assertEquals(0, transfer.getTotalAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("调出与调入仓库相同时拒绝")
    void shouldRejectSameWarehouse() {
        Transfer transfer = draftTransfer();
        transfer.setToWarehouseId(transfer.getFromWarehouseId());

        BizException exception = assertThrows(BizException.class, transfer::calculateTotal);

        assertEquals(ErpErrorCode.TRANSFER_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("无分录时拒绝")
    void shouldRejectEmptyLines() {
        Transfer transfer = draftTransfer();
        transfer.setLines(List.of());

        BizException exception = assertThrows(BizException.class, transfer::calculateTotal);

        assertEquals(ErpErrorCode.QUANTITY_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("草稿可过账一次，重复过账拒绝")
    void shouldPostOnce() {
        Transfer transfer = draftTransfer();
        transfer.calculateTotal();
        transfer.post();

        assertEquals(ScmStatus.POSTED, transfer.getStatus());
        BizException exception = assertThrows(BizException.class, transfer::post);
        assertEquals(ErpErrorCode.TRANSFER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("已过账调拨单不可取消")
    void shouldRejectCancelAfterPost() {
        Transfer transfer = draftTransfer();
        transfer.calculateTotal();
        transfer.post();

        BizException exception = assertThrows(BizException.class, transfer::cancel);

        assertEquals(ErpErrorCode.TRANSFER_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造草稿调拨单。
     *
     * @return 调拨单
     */
    private Transfer draftTransfer() {
        Transfer transfer = new Transfer();
        transfer.setStatus(ScmStatus.DRAFT);
        transfer.setFromWarehouseId(1L);
        transfer.setToWarehouseId(2L);
        TransferLine line = new TransferLine();
        line.setLineNo(1);
        line.setItemCode("ERP-001");
        line.setItemName("测试物料");
        line.setQuantity(new BigDecimal("5"));
        line.setUnitCost(BigDecimal.ZERO);
        line.setAmount(BigDecimal.ZERO);
        transfer.setLines(List.of(line));
        return transfer;
    }
}
