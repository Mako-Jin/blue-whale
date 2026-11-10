package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MeasuringTool} 量具校准规则单元测试。
 *
 * @author BlueWhale
 */
class MeasuringToolTest {

    @Test
    @DisplayName("校准到期日当天仍可用于检验")
    void shouldBeUsableOnDueDate() {
        MeasuringTool tool = tool();

        assertTrue(tool.usableOn(LocalDate.of(2026, 11, 4)));
    }

    @Test
    @DisplayName("校准过期后不可用于检验")
    void shouldNotBeUsableAfterExpired() {
        MeasuringTool tool = tool();

        assertFalse(tool.usableOn(LocalDate.of(2026, 11, 5)));
    }

    @Test
    @DisplayName("停用量具不可用于检验")
    void shouldNotBeUsableWhenDisabled() {
        MeasuringTool tool = tool();
        tool.setStatus(QcStatus.DISABLED);

        assertFalse(tool.usableOn(LocalDate.of(2026, 11, 1)));
    }

    @Test
    @DisplayName("校准合格后顺延下次校准日期")
    void shouldAdvanceAfterCalibration() {
        MeasuringTool tool = tool();

        tool.calibrated(LocalDate.of(2026, 11, 4));

        assertEquals(LocalDate.of(2026, 11, 4), tool.getLastCalibrationDate());
        assertEquals(LocalDate.of(2027, 11, 4), tool.getNextCalibrationDate());
    }

    @Test
    @DisplayName("校准周期为零时拒绝建档")
    void shouldRejectInvalidCycle() {
        MeasuringTool tool = tool();
        tool.setCalibrationCycleMonths(0);

        BizException exception = assertThrows(BizException.class, tool::validateForCreate);

        assertEquals(ErpErrorCode.QC_INVALID, exception.getCode());
    }

    /**
     * 构造量具。
     *
     * @return 量具
     */
    private MeasuringTool tool() {
        MeasuringTool tool = new MeasuringTool();
        tool.setToolNo("T001");
        tool.setName("卡尺");
        tool.setCalibrationCycleMonths(12);
        tool.setLastCalibrationDate(LocalDate.of(2025, 11, 4));
        tool.setNextCalibrationDate(LocalDate.of(2026, 11, 4));
        tool.setStatus(QcStatus.ENABLED);
        return tool;
    }
}
