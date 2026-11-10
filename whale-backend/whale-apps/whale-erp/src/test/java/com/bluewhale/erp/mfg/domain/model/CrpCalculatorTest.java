package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CrpCalculator} 单元测试。
 *
 * @author BlueWhale
 */
class CrpCalculatorTest {

    /** 周一 */
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    @Test
    @DisplayName("无限负荷按计划窗口分摊并标记超载")
    void shouldMarkOverload() {
        CrpResult result = CrpCalculator.calculate(List.of(order(MONDAY, MONDAY, operation(10, 2))),
                List.of(center("WC1", "8", BigDecimal.valueOf(100))), MONDAY, MONDAY.plusDays(6));

        CrpLoadBucket monday = result.getLoads().stream()
                .filter(bucket -> bucket.getDate().equals(MONDAY))
                .findFirst().orElseThrow();
        assertEquals(0, monday.getCapacityHours().compareTo(new BigDecimal("8")));
        assertEquals(0, monday.getLoadHours().compareTo(new BigDecimal("20")));
        assertEquals(0, monday.getUtilizationPercent().compareTo(new BigDecimal("250")));
        assertTrue(monday.isOverload());
    }

    @Test
    @DisplayName("有限排程占用日产能并顺延至工作日")
    void shouldScheduleAcrossDays() {
        CrpResult result = CrpCalculator.calculate(List.of(order(MONDAY, MONDAY, operation(10, 2))),
                List.of(center("WC1", "8", BigDecimal.valueOf(100))), MONDAY, MONDAY.plusDays(6));

        CrpScheduleSlot slot = result.getSchedule().get(0);
        assertEquals(MONDAY, slot.getStartDate());
        assertEquals(LocalDate.of(2026, 11, 4), slot.getEndDate());
        assertEquals(0, slot.getHours().compareTo(new BigDecimal("20")));
        assertTrue(slot.isDelayed());
    }

    @Test
    @DisplayName("前道未完成时后道从下一工作日开始")
    void shouldRespectOperationSequence() {
        ProductionOrder order = order(MONDAY, MONDAY,
                operation(10, 2), operationWithSetup(20, BigDecimal.ONE, BigDecimal.ONE));
        order.getOperations().get(0).setPlannedQuantity(new BigDecimal("10"));
        order.getOperations().get(1).setPlannedQuantity(new BigDecimal("10"));

        CrpResult result = CrpCalculator.calculate(List.of(order),
                List.of(center("WC1", "8", BigDecimal.valueOf(100)), center("WC2", "8", BigDecimal.valueOf(100))),
                MONDAY, MONDAY.plusDays(6));

        CrpScheduleSlot second = result.getSchedule().stream()
                .filter(slot -> slot.getStepNo().equals(20)).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 11, 5), second.getStartDate());
        assertEquals(0, second.getHours().compareTo(new BigDecimal("11")));
    }

    @Test
    @DisplayName("非工作日不排产")
    void shouldSkipWeekend() {
        LocalDate saturday = LocalDate.of(2026, 11, 7);
        ProductionOrder order = order(saturday, saturday, operation(1, 8));
        order.setPlannedQuantity(BigDecimal.ONE);
        order.getOperations().get(0).setPlannedQuantity(BigDecimal.ONE);
        CrpResult result = CrpCalculator.calculate(List.of(order),
                List.of(center("WC1", "8", BigDecimal.valueOf(100))), saturday, saturday.plusDays(9));

        CrpScheduleSlot slot = result.getSchedule().get(0);
        assertEquals(LocalDate.of(2026, 11, 9), slot.getStartDate());
        assertEquals(LocalDate.of(2026, 11, 9), slot.getEndDate());
    }

    @Test
    @DisplayName("未维护工作中心的订单被排除")
    void shouldExcludeMissingWorkCenter() {
        CrpResult result = CrpCalculator.calculate(List.of(order(MONDAY, MONDAY, operation(10, 2))),
                List.of(), MONDAY, MONDAY.plusDays(6));

        assertEquals(1, result.getExcludedOrderCount());
        assertEquals(0, result.getSchedule().size());
    }

    @Test
    @DisplayName("查询区间超过 62 天拒绝")
    void shouldRejectLongRange() {
        assertThrows(BizException.class, () -> CrpCalculator.calculate(List.of(),
                List.of(center("WC1", "8", BigDecimal.valueOf(100))), MONDAY, MONDAY.plusDays(62)));
    }

    /**
     * 构造工作中心。
     *
     * @param code       编码
     * @param capacity   日产能
     * @param efficiency 效率
     * @return 工作中心
     */
    private WorkCenter center(String code, String capacity, BigDecimal efficiency) {
        WorkCenter center = new WorkCenter();
        center.setCode(code);
        center.setName(code);
        center.setDailyCapacityHours(new BigDecimal(capacity));
        center.setEfficiencyRate(efficiency);
        center.setWorkDays("1,2,3,4,5");
        center.setStatus(MfgStatus.ENABLED);
        return center;
    }

    /**
     * 构造生产订单。
     *
     * @param start      计划开工
     * @param end        计划完工
     * @param operations 工序
     * @return 生产订单
     */
    private ProductionOrder order(LocalDate start, LocalDate end, ProductionOperation... operations) {
        ProductionOrder order = new ProductionOrder();
        order.setId(1L);
        order.setOrderNo("MO-20261102-0001");
        order.setItemCode("ERP-001");
        order.setPlannedQuantity(new BigDecimal("10"));
        order.setCompletedQuantity(BigDecimal.ZERO);
        order.setPlanStartDate(start);
        order.setPlanEndDate(end);
        order.setStatus(MfgStatus.RELEASED);
        order.setOperations(List.of(operations));
        return order;
    }

    /**
     * 构造工序。
     *
     * @param stepNo        序号
     * @param standardHours 单件工时
     * @return 工序
     */
    private ProductionOperation operation(Integer stepNo, int standardHours) {
        return operationWithSetup(stepNo, BigDecimal.ZERO, BigDecimal.valueOf(standardHours));
    }

    /**
     * 构造带准备工时的工序。
     *
     * @param stepNo        序号
     * @param setupHours    准备工时
     * @param standardHours 单件工时
     * @return 工序
     */
    private ProductionOperation operationWithSetup(Integer stepNo, BigDecimal setupHours,
                                                   BigDecimal standardHours) {
        ProductionOperation operation = new ProductionOperation();
        operation.setStepNo(stepNo);
        operation.setName("工序" + stepNo);
        operation.setWorkCenterCode(stepNo == 20 ? "WC2" : "WC1");
        operation.setStandardHours(standardHours);
        operation.setSetupHours(setupHours);
        operation.setPlannedQuantity(new BigDecimal("10"));
        operation.setQualifiedQuantity(BigDecimal.ZERO);
        operation.setScrappedQuantity(BigDecimal.ZERO);
        operation.setStatus(MfgStatus.PENDING);
        return operation;
    }
}
