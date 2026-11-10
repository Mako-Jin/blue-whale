package com.bluewhale.erp.qc.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.qc.application.command.EquipmentSaveCommand;
import com.bluewhale.erp.qc.application.command.MaintenanceCompleteCommand;
import com.bluewhale.erp.qc.application.command.MaintenanceOrderCreateCommand;
import com.bluewhale.erp.qc.application.command.MaintenancePlanSaveCommand;
import com.bluewhale.erp.qc.application.command.OeeRecordCommand;
import com.bluewhale.erp.qc.application.view.EquipmentView;
import com.bluewhale.erp.qc.application.view.MaintenanceOrderView;
import com.bluewhale.erp.qc.application.view.OeeRecordView;
import com.bluewhale.erp.qc.domain.model.Equipment;
import com.bluewhale.erp.qc.domain.model.MaintenanceOrder;
import com.bluewhale.erp.qc.domain.model.MaintenancePlan;
import com.bluewhale.erp.qc.domain.model.OeeRecord;
import com.bluewhale.erp.qc.domain.model.QcStatus;
import com.bluewhale.erp.qc.domain.repository.EquipmentRepository;
import com.bluewhale.erp.qc.domain.repository.MaintenanceRepository;
import com.bluewhale.erp.qc.domain.repository.OeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EquipmentAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class EquipmentAppServiceImplTest {

    private final EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);

    private final MaintenanceRepository maintenanceRepository = mock(MaintenanceRepository.class);

    private final OeeRepository oeeRepository = mock(OeeRepository.class);

    private final EquipmentAppServiceImpl service = new EquipmentAppServiceImpl(equipmentRepository,
            maintenanceRepository, oeeRepository);

    @Test
    @DisplayName("新增设备默认运行状态")
    void shouldCreateEquipment() {
        when(equipmentRepository.findByEquipmentNo("EQ001")).thenReturn(Optional.empty());

        EquipmentView view = service.createEquipment(equipmentCommand());

        assertEquals(QcStatus.EQUIPMENT_RUNNING, view.getStatus());
        assertEquals("EQ001", view.getEquipmentNo());
        verify(equipmentRepository).insert(any(Equipment.class));
    }

    @Test
    @DisplayName("设备编码重复拒绝建档")
    void shouldRejectDuplicateEquipment() {
        when(equipmentRepository.findByEquipmentNo("EQ001")).thenReturn(Optional.of(equipment(QcStatus.EQUIPMENT_RUNNING)));

        BizException exception = assertThrows(BizException.class, () -> service.createEquipment(equipmentCommand()));

        assertEquals(ErpErrorCode.EQUIPMENT_CODE_CONFLICT, exception.getCode());
        verify(equipmentRepository, never()).insert(any(Equipment.class));
    }

    @Test
    @DisplayName("编辑不存在的设备返回 404")
    void shouldRejectUpdateMissingEquipment() {
        when(equipmentRepository.findByEquipmentNo("EQ001")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.updateEquipment("EQ001", equipmentCommand()));

        assertEquals(ErpErrorCode.EQUIPMENT_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("保养计划必须关联已存在设备")
    void shouldRejectPlanForMissingEquipment() {
        when(equipmentRepository.findByEquipmentNo("EQ001")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.createPlan(planCommand()));

        assertEquals(ErpErrorCode.EQUIPMENT_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("完成保养工单推进计划的上下次保养日")
    void shouldAdvancePlanOnComplete() {
        when(maintenanceRepository.findOrderById(1L)).thenReturn(Optional.of(order()));
        when(maintenanceRepository.findPlanById(3L)).thenReturn(Optional.of(plan()));

        MaintenanceOrderView view = service.completeOrder(1L, completeCommand());

        assertEquals(QcStatus.MAINTENANCE_COMPLETED, view.getStatus());
        verify(maintenanceRepository).updateOrder(any(MaintenanceOrder.class));
        ArgumentCaptor<MaintenancePlan> planCaptor = ArgumentCaptor.forClass(MaintenancePlan.class);
        verify(maintenanceRepository).updatePlan(planCaptor.capture());
        assertEquals(LocalDate.of(2026, 12, 4), planCaptor.getValue().getNextDate());
        assertEquals(LocalDate.of(2026, 11, 4), planCaptor.getValue().getLastDate());
    }

    @Test
    @DisplayName("OEE 首次回传新增记录")
    void shouldInsertOee() {
        when(equipmentRepository.findByEquipmentNo("EQ001")).thenReturn(Optional.of(equipment(QcStatus.EQUIPMENT_RUNNING)));
        when(oeeRepository.findByEquipmentAndDate("EQ001", LocalDate.of(2026, 11, 4)))
                .thenReturn(Optional.empty());

        OeeRecordView view = service.recordOee(oeeCommand());

        assertEquals(0, view.getOeeRate().compareTo(new BigDecimal("0.6480")));
        verify(oeeRepository).insert(any(OeeRecord.class));
    }

    @Test
    @DisplayName("OEE 重复回传覆盖同一设备日期记录")
    void shouldUpdateOeeOnDuplicate() {
        when(equipmentRepository.findByEquipmentNo("EQ001")).thenReturn(Optional.of(equipment(QcStatus.EQUIPMENT_RUNNING)));
        OeeRecord existing = new OeeRecord();
        existing.setId(8L);
        when(oeeRepository.findByEquipmentAndDate("EQ001", LocalDate.of(2026, 11, 4)))
                .thenReturn(Optional.of(existing));

        OeeRecordView view = service.recordOee(oeeCommand());

        assertEquals(8L, view.getId());
        verify(oeeRepository).update(any(OeeRecord.class));
        verify(oeeRepository, never()).insert(any(OeeRecord.class));
    }

    @Test
    @DisplayName("退役设备拒绝 OEE 回传")
    void shouldRejectOeeForRetiredEquipment() {
        when(equipmentRepository.findByEquipmentNo("EQ001")).thenReturn(Optional.of(equipment(QcStatus.EQUIPMENT_RETIRED)));

        BizException exception = assertThrows(BizException.class, () -> service.recordOee(oeeCommand()));

        assertEquals(ErpErrorCode.QC_INVALID, exception.getCode());
    }

    /**
     * 构建设备保存命令。
     *
     * @return 命令
     */
    private EquipmentSaveCommand equipmentCommand() {
        EquipmentSaveCommand command = new EquipmentSaveCommand();
        command.setEquipmentNo("EQ001");
        command.setName("注塑机");
        return command;
    }

    /**
     * 构建保养计划命令。
     *
     * @return 命令
     */
    private MaintenancePlanSaveCommand planCommand() {
        MaintenancePlanSaveCommand command = new MaintenancePlanSaveCommand();
        command.setEquipmentNo("EQ001");
        command.setCycleDays(30);
        command.setNextDate(LocalDate.of(2026, 11, 4));
        return command;
    }

    /**
     * 构建保养完成命令。
     *
     * @return 命令
     */
    private MaintenanceCompleteCommand completeCommand() {
        MaintenanceCompleteCommand command = new MaintenanceCompleteCommand();
        command.setCompleteDate(LocalDate.of(2026, 11, 4));
        command.setCostAmount(new BigDecimal("200"));
        command.setResult("更换润滑油");
        return command;
    }

    /**
     * 构建 OEE 命令。
     *
     * @return 命令
     */
    private OeeRecordCommand oeeCommand() {
        OeeRecordCommand command = new OeeRecordCommand();
        command.setEquipmentNo("EQ001");
        command.setRecordDate(LocalDate.of(2026, 11, 4));
        command.setPlannedMinutes(new BigDecimal("480"));
        command.setRunMinutes(new BigDecimal("432"));
        command.setDownMinutes(new BigDecimal("48"));
        command.setIdealQty(new BigDecimal("500"));
        command.setOutputQty(new BigDecimal("400"));
        command.setGoodQty(new BigDecimal("360"));
        return command;
    }

    /**
     * 构建设备。
     *
     * @param status 状态
     * @return 设备
     */
    private Equipment equipment(String status) {
        Equipment equipment = new Equipment();
        equipment.setId(1L);
        equipment.setEquipmentNo("EQ001");
        equipment.setName("注塑机");
        equipment.setStatus(status);
        return equipment;
    }

    /**
     * 构造保养计划。
     *
     * @return 保养计划
     */
    private MaintenancePlan plan() {
        MaintenancePlan plan = new MaintenancePlan();
        plan.setId(3L);
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
        order.setId(1L);
        order.setOrderNo("MTW-202611-0001");
        order.setEquipmentNo("EQ001");
        order.setPlanId(3L);
        order.setMaintenanceType(QcStatus.MAINTENANCE_PREVENTIVE);
        order.setStatus(QcStatus.MAINTENANCE_OPEN);
        order.setPlanDate(LocalDate.of(2026, 11, 4));
        order.setCostAmount(BigDecimal.ZERO);
        return order;
    }
}
