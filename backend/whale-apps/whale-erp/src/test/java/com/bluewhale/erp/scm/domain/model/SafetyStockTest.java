package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SafetyStock} 单元测试。
 *
 * @author BlueWhale
 */
class SafetyStockTest {

    @Test
    @DisplayName("校验通过：安全量为 0，最高库存为空")
    void shouldPassWhenMaxQtyAbsent() {
        SafetyStock safetyStock = safetyStock(new BigDecimal("0"), null);
        safetyStock.validate();
        assertEquals(0, safetyStock.getSafetyQty().signum());
    }

    @Test
    @DisplayName("校验通过：最高库存等于安全量")
    void shouldPassWhenMaxQtyEqualsSafetyQty() {
        safetyStock(new BigDecimal("10"), new BigDecimal("10")).validate();
    }

    @Test
    @DisplayName("校验失败：安全库存量为负")
    void shouldRejectNegativeSafetyQty() {
        BizException exception = assertThrows(BizException.class,
                () -> safetyStock(new BigDecimal("-1"), null).validate());
        assertEquals(ErpErrorCode.SAFETY_STOCK_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("校验失败：最高库存低于安全量")
    void shouldRejectMaxQtyLowerThanSafetyQty() {
        BizException exception = assertThrows(BizException.class,
                () -> safetyStock(new BigDecimal("10"), new BigDecimal("9")).validate());
        assertEquals(ErpErrorCode.SAFETY_STOCK_INVALID, exception.getCode());
    }

    /**
     * 构造安全库存。
     *
     * @param safetyQty 安全库存量
     * @param maxQty 最高库存量
     * @return 安全库存
     */
    private SafetyStock safetyStock(BigDecimal safetyQty, BigDecimal maxQty) {
        SafetyStock safetyStock = new SafetyStock();
        safetyStock.setWarehouseId(1L);
        safetyStock.setItemId(2L);
        safetyStock.setItemCode("ERP-001");
        safetyStock.setItemName("物料A");
        safetyStock.setSafetyQty(safetyQty);
        safetyStock.setMaxQty(maxQty);
        return safetyStock;
    }
}
