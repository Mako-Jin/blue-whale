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
 * {@link Stocktake} 与 {@link StocktakeLine} 领域规则测试。
 *
 * @author BlueWhale
 */
class StocktakeTest {

    @Test
    @DisplayName("盘亏差异与金额按单位成本计算")
    void shouldCalculateLoss() {
        StocktakeLine line = new StocktakeLine();
        line.setBookQuantity(new BigDecimal("10"));
        line.setCountedQuantity(new BigDecimal("8"));

        line.calculate(new BigDecimal("5"));

        assertEquals(0, line.getDifference().compareTo(new BigDecimal("-2")));
        assertEquals(0, line.getAmount().compareTo(new BigDecimal("-10")));
    }

    @Test
    @DisplayName("盘盈与盘亏金额分别汇总")
    void shouldRefreshTotals() {
        Stocktake stocktake = new Stocktake();
        stocktake.setStatus(ScmStatus.DRAFT);
        StocktakeLine gain = new StocktakeLine();
        gain.setBookQuantity(new BigDecimal("10"));
        gain.setCountedQuantity(new BigDecimal("12"));
        gain.calculate(new BigDecimal("5"));
        StocktakeLine loss = new StocktakeLine();
        loss.setBookQuantity(new BigDecimal("10"));
        loss.setCountedQuantity(new BigDecimal("7"));
        loss.calculate(new BigDecimal("5"));
        stocktake.setLines(List.of(gain, loss));

        stocktake.refreshTotals();

        assertEquals(0, stocktake.getGainAmount().compareTo(new BigDecimal("10")));
        assertEquals(0, stocktake.getLossAmount().compareTo(new BigDecimal("15")));
    }

    @Test
    @DisplayName("草稿可过账一次，重复过账拒绝")
    void shouldPostOnce() {
        Stocktake stocktake = draftStocktake();
        stocktake.post();

        assertEquals(ScmStatus.POSTED, stocktake.getStatus());
        BizException exception = assertThrows(BizException.class, stocktake::post);
        assertEquals(ErpErrorCode.STOCKTAKE_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("已过账盘点单不可取消")
    void shouldRejectCancelAfterPost() {
        Stocktake stocktake = draftStocktake();
        stocktake.post();

        BizException exception = assertThrows(BizException.class, stocktake::cancel);

        assertEquals(ErpErrorCode.STOCKTAKE_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造草稿盘点单。
     *
     * @return 盘点单
     */
    private Stocktake draftStocktake() {
        Stocktake stocktake = new Stocktake();
        stocktake.setStatus(ScmStatus.DRAFT);
        stocktake.setWarehouseId(1L);
        StocktakeLine line = new StocktakeLine();
        line.setLineNo(1);
        line.setItemCode("ERP-001");
        line.setBookQuantity(new BigDecimal("5"));
        line.setCountedQuantity(new BigDecimal("6"));
        line.calculate(new BigDecimal("2"));
        stocktake.setLines(List.of(line));
        return stocktake;
    }
}
