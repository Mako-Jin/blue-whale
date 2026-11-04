package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link OeeRecord} OEE 计算与参数不变量单元测试。
 *
 * @author BlueWhale
 */
class OeeRecordTest {

    @Test
    @DisplayName("按时间/性能/合格率计算 OEE")
    void shouldComputeOee() {
        OeeRecord record = record();

        record.compute();

        assertEquals(0, record.getAvailabilityRate().compareTo(new BigDecimal("0.9")));
        assertEquals(0, record.getPerformanceRate().compareTo(new BigDecimal("0.8")));
        assertEquals(0, record.getQualityRate().compareTo(new BigDecimal("0.9")));
        assertEquals(0, record.getOeeRate().compareTo(new BigDecimal("0.6480")));
    }

    @Test
    @DisplayName("比率封顶为 1")
    void shouldClampRates() {
        OeeRecord record = record();
        record.setRunMinutes(new BigDecimal("600"));
        record.setOutputQty(new BigDecimal("600"));
        record.setGoodQty(new BigDecimal("600"));

        record.compute();

        assertEquals(0, record.getAvailabilityRate().compareTo(BigDecimal.ONE));
        assertEquals(0, record.getPerformanceRate().compareTo(BigDecimal.ONE));
        assertEquals(0, record.getQualityRate().compareTo(BigDecimal.ONE));
        assertEquals(0, record.getOeeRate().compareTo(BigDecimal.ONE));
    }

    @Test
    @DisplayName("合格产量大于实际产量时拒绝")
    void shouldRejectGoodGreaterThanOutput() {
        OeeRecord record = record();
        record.setGoodQty(new BigDecimal("500"));

        BizException exception = assertThrows(BizException.class, record::compute);

        assertEquals(ErpErrorCode.OEE_PARAM_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("计划运行分钟为零时拒绝")
    void shouldRejectZeroPlannedMinutes() {
        OeeRecord record = record();
        record.setPlannedMinutes(BigDecimal.ZERO);

        BizException exception = assertThrows(BizException.class, record::compute);

        assertEquals(ErpErrorCode.OEE_PARAM_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("实际产量为零时合格率为零")
    void shouldUseZeroQualityRateWhenNoOutput() {
        OeeRecord record = record();
        record.setOutputQty(BigDecimal.ZERO);
        record.setGoodQty(BigDecimal.ZERO);

        record.compute();

        assertEquals(0, record.getQualityRate().compareTo(BigDecimal.ZERO));
        assertEquals(0, record.getOeeRate().compareTo(BigDecimal.ZERO));
    }

    /**
     * 构造 OEE 记录。
     *
     * @return OEE 记录
     */
    private OeeRecord record() {
        OeeRecord record = new OeeRecord();
        record.setEquipmentNo("EQ001");
        record.setRecordDate(LocalDate.of(2026, 11, 4));
        record.setPlannedMinutes(new BigDecimal("480"));
        record.setRunMinutes(new BigDecimal("432"));
        record.setDownMinutes(new BigDecimal("48"));
        record.setIdealQty(new BigDecimal("500"));
        record.setOutputQty(new BigDecimal("400"));
        record.setGoodQty(new BigDecimal("360"));
        return record;
    }
}
