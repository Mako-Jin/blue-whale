package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Nonconformity} 不合格品单不变量单元测试。
 *
 * @author BlueWhale
 */
class NonconformityTest {

    @Test
    @DisplayName("缺陷描述为空时拒绝建单")
    void shouldRejectMissingDefect() {
        Nonconformity ncr = ncr();
        ncr.setDefectDesc(null);

        BizException exception = assertThrows(BizException.class, ncr::validateForCreate);

        assertEquals(ErpErrorCode.QC_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("处置方式非法时拒绝处置")
    void shouldRejectInvalidDisposition() {
        Nonconformity ncr = ncr();

        BizException exception = assertThrows(BizException.class,
                () -> ncr.dispose("UNKNOWN", LocalDate.of(2026, 11, 4), BigDecimal.ZERO, null));

        assertEquals(ErpErrorCode.NCR_DISPOSITION_REQUIRED, exception.getCode());
    }

    @Test
    @DisplayName("处置待处置单后状态为 DISPOSED 并记录成本类型")
    void shouldDispose() {
        Nonconformity ncr = ncr();

        ncr.dispose(QcStatus.DISPOSITION_REWORK, LocalDate.of(2026, 11, 4), new BigDecimal("120"), "返工完成");

        assertEquals(QcStatus.NCR_DISPOSED, ncr.getStatus());
        assertEquals(QcStatus.COST_REWORK, ncr.costType());
        assertTrue(ncr.costRequired());
        assertEquals(LocalDate.of(2026, 11, 4), ncr.getDisposeDate());
    }

    @Test
    @DisplayName("报废处置归集报废质量成本")
    void shouldMapScrapCostType() {
        Nonconformity ncr = ncr();

        ncr.dispose(QcStatus.DISPOSITION_SCRAP, LocalDate.of(2026, 11, 4), BigDecimal.ZERO, null);

        assertEquals(QcStatus.COST_SCRAP, ncr.costType());
        assertFalse(ncr.costRequired());
    }

    @Test
    @DisplayName("未处置不可关闭")
    void shouldRejectCloseBeforeDispose() {
        Nonconformity ncr = ncr();

        BizException exception = assertThrows(BizException.class,
                () -> ncr.close(LocalDate.of(2026, 11, 5), null));

        assertEquals(ErpErrorCode.QC_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("处置后可关闭且不可重复处置")
    void shouldCloseAfterDispose() {
        Nonconformity ncr = ncr();
        ncr.dispose(QcStatus.DISPOSITION_RETURN, LocalDate.of(2026, 11, 4), BigDecimal.ZERO, null);

        ncr.close(LocalDate.of(2026, 11, 5), "已退货");

        assertEquals(QcStatus.NCR_CLOSED, ncr.getStatus());
        assertEquals(LocalDate.of(2026, 11, 5), ncr.getCloseDate());
        BizException exception = assertThrows(BizException.class,
                () -> ncr.dispose(QcStatus.DISPOSITION_SCRAP, LocalDate.of(2026, 11, 6), BigDecimal.ZERO, null));
        assertEquals(ErpErrorCode.QC_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造待处置不合格品单。
     *
     * @return 不合格品单
     */
    private Nonconformity ncr() {
        Nonconformity ncr = new Nonconformity();
        ncr.setNcrNo("NCR-202611-0001");
        ncr.setSourceType(QcStatus.SOURCE_INSPECTION);
        ncr.setSourceNo("QC-20261104-0001");
        ncr.setItemName("螺栓");
        ncr.setQuantity(new BigDecimal("2"));
        ncr.setDefectDesc("尺寸超差");
        ncr.setStatus(QcStatus.NCR_OPEN);
        ncr.setCostAmount(BigDecimal.ZERO);
        ncr.validateForCreate();
        return ncr;
    }
}
