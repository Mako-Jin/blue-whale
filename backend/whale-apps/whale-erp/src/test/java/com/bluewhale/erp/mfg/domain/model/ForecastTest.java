package com.bluewhale.erp.mfg.domain.model;

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
 * {@link Forecast} 单元测试。
 *
 * @author BlueWhale
 */
class ForecastTest {

    @Test
    @DisplayName("汇总预测数量")
    void shouldCalculateTotal() {
        Forecast forecast = newForecast();

        forecast.calculateTotal();

        assertEquals(0, forecast.getTotalQuantity().compareTo(new BigDecimal("30")));
    }

    @Test
    @DisplayName("空分录拒绝建单")
    void shouldRejectEmptyLines() {
        Forecast forecast = newForecast();
        forecast.setLines(new ArrayList<>());

        BizException exception = assertThrows(BizException.class, forecast::calculateTotal);

        assertEquals(ErpErrorCode.QUANTITY_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("发布后状态为已发布")
    void shouldPublish() {
        Forecast forecast = newForecast();
        forecast.setStatus(MfgStatus.DRAFT);

        forecast.publish();

        assertEquals(MfgStatus.PUBLISHED, forecast.getStatus());
    }

    @Test
    @DisplayName("重复发布拒绝")
    void shouldRejectPublishTwice() {
        Forecast forecast = newForecast();
        forecast.setStatus(MfgStatus.PUBLISHED);

        BizException exception = assertThrows(BizException.class, forecast::publish);

        assertEquals(ErpErrorCode.MFG_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("已发布预测可以取消")
    void shouldCancelPublished() {
        Forecast forecast = newForecast();
        forecast.setStatus(MfgStatus.PUBLISHED);

        forecast.cancel();

        assertEquals(MfgStatus.CANCELLED, forecast.getStatus());
    }

    /**
     * 构造预测批次。
     *
     * @return 预测批次
     */
    private Forecast newForecast() {
        Forecast forecast = new Forecast();
        ForecastLine first = new ForecastLine();
        first.setLineNo(1);
        first.setItemCode("ERP-001");
        first.setQuantity(new BigDecimal("10"));
        ForecastLine second = new ForecastLine();
        second.setLineNo(2);
        second.setItemCode("ERP-002");
        second.setQuantity(new BigDecimal("20"));
        forecast.setLines(List.of(first, second));
        return forecast;
    }
}
