package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StockBalance} 移动加权平均规则单元测试。
 *
 * @author BlueWhale
 */
class StockBalanceTest {

    @Test
    @DisplayName("两次入库后单位成本为加权平均")
    void shouldWeightAverageOnInbound() {
        StockBalance balance = balance();

        balance.inbound(new BigDecimal("10"), new BigDecimal("2"));
        balance.inbound(new BigDecimal("10"), new BigDecimal("4"));

        assertEquals(0, balance.getQuantity().compareTo(new BigDecimal("20")));
        assertEquals(0, balance.getAmount().compareTo(new BigDecimal("60")));
    }

    @Test
    @DisplayName("出库按加权平均结转成本")
    void shouldOutboundAtAverageCost() {
        StockBalance balance = balance();
        balance.inbound(new BigDecimal("10"), new BigDecimal("2"));
        balance.inbound(new BigDecimal("10"), new BigDecimal("4"));

        BigDecimal cost = balance.outbound(new BigDecimal("5"));

        assertEquals(0, cost.compareTo(new BigDecimal("15")));
        assertEquals(0, balance.getQuantity().compareTo(new BigDecimal("15")));
        assertEquals(0, balance.getAmount().compareTo(new BigDecimal("45")));
    }

    @Test
    @DisplayName("库存不足拒绝出库")
    void shouldRejectNegativeStock() {
        StockBalance balance = balance();
        balance.inbound(new BigDecimal("3"), new BigDecimal("5"));

        BizException exception = assertThrows(BizException.class,
                () -> balance.outbound(new BigDecimal("4")));

        assertEquals(ErpErrorCode.NEGATIVE_STOCK, exception.getCode());
    }

    @Test
    @DisplayName("出清后金额与数量归零")
    void shouldResetWhenEmpty() {
        StockBalance balance = balance();
        balance.inbound(new BigDecimal("3"), new BigDecimal("5"));

        balance.outbound(new BigDecimal("3"));

        assertTrue(balance.empty());
        assertEquals(0, balance.getAmount().compareTo(BigDecimal.ZERO));
    }

    /**
     * 构造空余额。
     *
     * @return 库存余额
     */
    private StockBalance balance() {
        StockBalance balance = new StockBalance();
        balance.setItemCode("ERP-001");
        balance.setQuantity(BigDecimal.ZERO);
        balance.setAmount(BigDecimal.ZERO);
        return balance;
    }
}
