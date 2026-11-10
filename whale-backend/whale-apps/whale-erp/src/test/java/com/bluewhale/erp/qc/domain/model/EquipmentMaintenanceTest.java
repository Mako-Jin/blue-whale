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
 * {@link Equipment}、{@link MaintenancePlan} 与 {@link MaintenanceOrder} 设备保养规则单元测试。
 *
 * @author BlueWhale
 */
class EquipmentMaintenanceTest {

    @Test
    @DisplayName("设备状态非法时拒绝建档")
    void shouldRejectInvalidEquipmentStatus() {
        Equipment equipment = new Equipment();
        equipment.setEquipmentNo("EQ001");
        equipment.setName("注塑机");
        equipment.setStatus("BROKEN");

        BizException exception = assertThrows(BizException.class, equipment::validateForCreate);

        assertEquals(ErpErrorCode.QC_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("保养完成按周期顺延下次保养日")
    void shouldAdvanceMaintenancePlan() {
        MaintenancePlan plan = plan();

        plan.advance(LocalDate.of(2026, 11, 4));

        assertEquals(LocalDate.of(2026, 11, 4), plan.getLastDate());
        assertEquals(LocalDate.of(2026, 12, 4), plan.getNextDate());
    }

    @Test
    @DisplayName("保养周期为零时拒绝建档")
    void shouldRejectInvalidCycleDays() {
        MaintenancePlan plan = plan();
        plan.setCycleDays(0);

        BizException exception = assertThrows(BizException.class, plan::validateForCreate);

        assertEquals(ErpErrorCode.QC_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("保养工单完成后登记费用与结果")
    void shouldCompleteMaintenanceOrder() {
        MaintenanceOrder order = order();

        order.complete(LocalDate.of(2026, 11, 4), new BigDecimal("200"), "更换润滑油");

        assertEquals(QcStatus.MAINTENANCE_COMPLETED, order.getStatus());
        assertEquals(LocalDate.of(2026, 11, 4), order.getCompleteDate());
        assertEquals(0, order.getCostAmount().compareTo(new BigDecimal("200")));
    }

    @Test
    @DisplayName("保养工单不可重复完成")
    void shouldRejectDuplicateComplete() {
        MaintenanceOrder order = order();
        order.complete(LocalDate.of(2026, 11, 4), BigDecimal.ZERO, "完成");

        BizException exception = assertThrows(BizException.class,
                () -> order.complete(LocalDate.of(2026, 11, 5), BigDecimal.ZERO, "重复"));

        assertEquals(ErpErrorCode.QC_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("完成日期早于计划日期时拒绝")
    void shouldRejectCompleteBeforePlan() {
        MaintenanceOrder order = order();

        BizException exception = assertThrows(BizException.class,
                () -> order.complete(LocalDate.of(2026, 11, 1), BigDecimal.ZERO, "提前"));

        assertEquals(ErpErrorCode.DOC_DATE_INVALID, exception.getCode());
    }

    /**
     * 构造保养计划。
     *
     * @return 保养计划
     */
    private MaintenancePlan plan() {
        MaintenancePlan plan = new MaintenancePlan();
        plan.setPlanNo("MT-202611-0001");
        plan.setEquipmentNo("EQ001");
        plan.setCycleDays(30);
        plan.setLastDate(LocalDate.of(2026, 10, 5));
        plan.setNextDate(LocalDate.of(2026, 11, 4));
        plan.setStatus(QcStatus.ENABLED);
        return plan;
    }

    /**
     * 构造保养工单。
     *
     * @return 保养工单
     */
    private MaintenanceOrder order() {
        MaintenanceOrder order = new MaintenanceOrder();
        order.setOrderNo("MTW-202611-0001");
        order.setEquipmentNo("EQ001");
        order.setMaintenanceType(QcStatus.MAINTENANCE_PREVENTIVE);
        order.setStatus(QcStatus.MAINTENANCE_OPEN);
        order.setPlanDate(LocalDate.of(2026, 11, 4));
        order.setCostAmount(BigDecimal.ZERO);
        return order;
    }
}
