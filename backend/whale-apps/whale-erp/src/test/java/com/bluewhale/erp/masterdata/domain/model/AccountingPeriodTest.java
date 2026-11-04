package com.bluewhale.erp.masterdata.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AccountingPeriod} 领域规则单元测试。
 *
 * @author BlueWhale
 */
class AccountingPeriodTest {

    @Test
    @DisplayName("关账后状态为 CLOSED 并记录操作人")
    void shouldClosePeriod() {
        AccountingPeriod period = period();

        period.close(9L, LocalDateTime.of(2026, 11, 3, 9, 0));

        assertEquals(MasterDataStatus.PERIOD_CLOSED, period.getStatus());
        assertEquals(9L, period.getClosedBy());
        assertTrue(period.getClosedAt() != null);
    }

    @Test
    @DisplayName("重复关账被拒绝")
    void shouldRejectDoubleClose() {
        AccountingPeriod period = period();
        period.close(9L, LocalDateTime.now());

        BizException exception = assertThrows(BizException.class,
                () -> period.close(9L, LocalDateTime.now()));

        assertEquals(ErpErrorCode.PERIOD_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("反结账恢复 OPEN 并清空关账信息")
    void shouldReopenPeriod() {
        AccountingPeriod period = period();
        period.close(9L, LocalDateTime.now());

        period.reopen();

        assertEquals(MasterDataStatus.PERIOD_OPEN, period.getStatus());
        assertNull(period.getClosedAt());
        assertNull(period.getClosedBy());
    }

    @Test
    @DisplayName("未关账期间不能反结账")
    void shouldRejectReopenWhenOpen() {
        AccountingPeriod period = period();

        BizException exception = assertThrows(BizException.class, period::reopen);

        assertEquals(ErpErrorCode.PERIOD_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("日期落期间判断")
    void shouldContainDate() {
        AccountingPeriod period = period();

        assertTrue(period.contains(LocalDate.of(2026, 11, 1)));
        assertTrue(period.contains(LocalDate.of(2026, 11, 30)));
        assertFalse(period.contains(LocalDate.of(2026, 12, 1)));
    }

    /**
     * 构造 2026-11 期间。
     *
     * @return 期间
     */
    private static AccountingPeriod period() {
        AccountingPeriod period = new AccountingPeriod();
        period.setPeriodCode("202611");
        period.setStartDate(LocalDate.of(2026, 11, 1));
        period.setEndDate(LocalDate.of(2026, 11, 30));
        period.setStatus(MasterDataStatus.PERIOD_OPEN);
        return period;
    }
}
