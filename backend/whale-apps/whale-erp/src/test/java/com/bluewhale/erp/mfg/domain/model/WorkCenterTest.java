package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link WorkCenter} 单元测试。
 *
 * @author BlueWhale
 */
class WorkCenterTest {

    @Test
    @DisplayName("效率折算日产能，非工作日为 0")
    void shouldCalculateCapacity() {
        WorkCenter center = center("80", "1,2,3,4,5");

        assertEquals(0, center.capacityOn(LocalDate.of(2026, 11, 2)).compareTo(new BigDecimal("6.4")));
        assertEquals(0, center.capacityOn(LocalDate.of(2026, 11, 7)).compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("效率越界拒绝")
    void shouldRejectInvalidEfficiency() {
        WorkCenter center = center("120", "1,2,3,4,5");

        assertThrows(BizException.class, center::validate);
    }

    @Test
    @DisplayName("工作日为空拒绝")
    void shouldRejectEmptyWorkDays() {
        WorkCenter center = center("80", "");

        assertThrows(BizException.class, center::validate);
    }

    /**
     * 构造工作中心。
     *
     * @param efficiency 效率
     * @param workDays   工作日
     * @return 工作中心
     */
    private WorkCenter center(String efficiency, String workDays) {
        WorkCenter center = new WorkCenter();
        center.setCode("WC1");
        center.setName("一号工作中心");
        center.setDailyCapacityHours(new BigDecimal("8"));
        center.setEfficiencyRate(new BigDecimal(efficiency));
        center.setWorkDays(workDays);
        center.setStatus(MfgStatus.ENABLED);
        return center;
    }
}
