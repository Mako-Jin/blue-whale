package com.bluewhale.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.equipment.dto.MesEquipmentSaveRequest;
import com.bluewhale.mes.equipment.dto.MesEquipmentStatusChangeRequest;
import com.bluewhale.mes.equipment.dto.MesMaintenanceOrderCompleteRequest;
import com.bluewhale.mes.equipment.dto.MesOeeSnapshotRequest;
import com.bluewhale.mes.equipment.dto.MesRepairActionRequest;
import com.bluewhale.mes.equipment.dto.MesSparePartStockRequest;
import com.bluewhale.mes.equipment.entity.MesEquipment;
import com.bluewhale.mes.equipment.entity.MesEquipmentDowntime;
import com.bluewhale.mes.equipment.entity.MesEquipmentStatusEvent;
import com.bluewhale.mes.equipment.entity.MesMaintenanceOrder;
import com.bluewhale.mes.equipment.entity.MesMaintenancePlan;
import com.bluewhale.mes.equipment.entity.MesOeeSnapshot;
import com.bluewhale.mes.equipment.entity.MesRepairOrder;
import com.bluewhale.mes.equipment.entity.MesSparePart;
import com.bluewhale.mes.equipment.entity.MesSparePartMovement;
import com.bluewhale.mes.equipment.entity.MesSparePartStock;
import com.bluewhale.mes.equipment.mapper.MesEquipmentDowntimeMapper;
import com.bluewhale.mes.equipment.mapper.MesEquipmentMapper;
import com.bluewhale.mes.equipment.mapper.MesEquipmentStatusEventMapper;
import com.bluewhale.mes.equipment.mapper.MesMaintenanceOrderMapper;
import com.bluewhale.mes.equipment.mapper.MesMaintenancePlanMapper;
import com.bluewhale.mes.equipment.mapper.MesOeeSnapshotMapper;
import com.bluewhale.mes.equipment.mapper.MesRepairOrderMapper;
import com.bluewhale.mes.equipment.mapper.MesSparePartMapper;
import com.bluewhale.mes.equipment.mapper.MesSparePartMovementMapper;
import com.bluewhale.mes.equipment.mapper.MesSparePartStockMapper;
import com.bluewhale.mes.equipment.vo.MesEquipmentGateVO;
import com.bluewhale.mes.equipment.vo.MesMaintenanceOrderVO;
import com.bluewhale.mes.equipment.vo.MesOeeSnapshotVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 设备服务单元测试（M4）。
 *
 * <p>覆盖设备编码唯一、状态事件追加与故障停机、开工门禁、保养状态机与保养项结论、
 * 维修闭环、备件库存非负与 OEE 默认公式。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesEquipmentServiceImplTest {

    @Mock
    private MesEquipmentMapper equipmentMapper;

    @Mock
    private MesEquipmentStatusEventMapper statusEventMapper;

    @Mock
    private MesEquipmentDowntimeMapper downtimeMapper;

    @Mock
    private MesMaintenancePlanMapper maintenancePlanMapper;

    @Mock
    private MesMaintenanceOrderMapper maintenanceOrderMapper;

    @Mock
    private MesRepairOrderMapper repairOrderMapper;

    @Mock
    private MesSparePartMapper sparePartMapper;

    @Mock
    private MesSparePartStockMapper sparePartStockMapper;

    @Mock
    private MesSparePartMovementMapper sparePartMovementMapper;

    @Mock
    private MesOeeSnapshotMapper oeeSnapshotMapper;

    @InjectMocks
    private MesEquipmentServiceImpl equipmentService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(MesEquipment.class, MesEquipmentStatusEvent.class, MesEquipmentDowntime.class,
                MesMaintenancePlan.class, MesMaintenanceOrder.class, MesRepairOrder.class, MesSparePart.class,
                MesSparePartStock.class, MesSparePartMovement.class, MesOeeSnapshot.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @Test
    @DisplayName("设备编码租户内唯一")
    void shouldRejectDuplicateEquipmentCode() {
        MesEquipmentSaveRequest request = new MesEquipmentSaveRequest();
        request.setEquipmentCode("EQ-001");
        request.setEquipmentName("冲压机");
        when(equipmentMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> equipmentService.saveEquipment(request));
        assertTrue(exception.getMessage().contains("设备编码已存在"));
        verify(equipmentMapper, never()).insert(any(MesEquipment.class));
    }

    @Test
    @DisplayName("状态变更为故障时追加事件并开停机单")
    void shouldAppendStatusEventAndOpenDowntimeOnFault() {
        MesEquipment equipment = new MesEquipment();
        equipment.setId(1L);
        equipment.setEquipmentCode("EQ-001");
        equipment.setStatus("RUNNING");
        when(equipmentMapper.selectById(1L)).thenReturn(equipment);
        when(statusEventMapper.selectOne(any())).thenReturn(null);
        when(equipmentMapper.updateById(any(MesEquipment.class))).thenReturn(1);
        when(downtimeMapper.selectOne(any())).thenReturn(null);

        MesEquipmentStatusChangeRequest request = new MesEquipmentStatusChangeRequest();
        request.setToStatus("FAULT");
        request.setOperator("operator");
        equipmentService.changeStatus(1L, request);

        verify(statusEventMapper).insert(any(MesEquipmentStatusEvent.class));
        verify(downtimeMapper).insert(any(MesEquipmentDowntime.class));
    }

    @Test
    @DisplayName("故障设备开工门禁阻断")
    void shouldBlockFaultEquipment() {
        MesEquipment equipment = new MesEquipment();
        equipment.setEquipmentCode("EQ-001");
        equipment.setStatus("FAULT");
        equipment.setEnabled(1);
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);

        MesEquipmentGateVO gate = equipmentService.gate("EQ-001");
        assertFalse(gate.getPassed());
        assertTrue(gate.getMessage().contains("故障"));
    }

    @Test
    @DisplayName("保养完工必须覆盖全部保养项")
    void shouldRequireAllMaintenanceItems() {
        MesMaintenanceOrder order = new MesMaintenanceOrder();
        order.setId(5L);
        order.setStatus("IN_PROGRESS");
        order.setChecklist("润滑;紧固");
        when(maintenanceOrderMapper.selectById(5L)).thenReturn(order);

        MesMaintenanceOrderCompleteRequest request = new MesMaintenanceOrderCompleteRequest();
        request.setCompletedBy("worker");
        request.setChecklistResults(List.of("润滑=正常"));
        BizException exception = assertThrows(BizException.class,
                () -> equipmentService.completeMaintenanceOrder(5L, request));
        assertTrue(exception.getMessage().contains("紧固"));
        verify(maintenanceOrderMapper, never()).updateById(any(MesMaintenanceOrder.class));

        request.setChecklistResults(List.of("润滑=正常", "紧固=正常"));
        when(maintenanceOrderMapper.updateById(any(MesMaintenanceOrder.class))).thenReturn(1);
        MesMaintenanceOrderVO vo = equipmentService.completeMaintenanceOrder(5L, request);
        assertEquals("COMPLETED", vo.getStatus());
        assertEquals("润滑=正常;紧固=正常", vo.getChecklistResult());
    }

    @Test
    @DisplayName("维修工单状态机不可跳跃")
    void shouldRejectIllegalRepairTransition() {
        MesRepairOrder order = new MesRepairOrder();
        order.setId(9L);
        order.setStatus("REPAIRING");
        when(repairOrderMapper.selectById(9L)).thenReturn(order);

        MesRepairActionRequest request = new MesRepairActionRequest();
        request.setAssignee("technician");
        assertThrows(BizException.class, () -> equipmentService.assignRepair(9L, request));
        assertThrows(BizException.class, () -> equipmentService.closeRepair(9L, request));
    }

    @Test
    @DisplayName("备件领用库存不得为负")
    void shouldRejectStockOutExceedingBalance() {
        MesSparePart part = new MesSparePart();
        part.setId(3L);
        part.setPartCode("SP-001");
        when(sparePartMapper.selectOne(any())).thenReturn(part);
        MesSparePartStock stock = new MesSparePartStock();
        stock.setId(4L);
        stock.setPartId(3L);
        stock.setWarehouseCode("DEFAULT");
        stock.setQuantity(new BigDecimal("2"));
        when(sparePartStockMapper.selectOne(any())).thenReturn(stock);

        MesSparePartStockRequest request = new MesSparePartStockRequest();
        request.setPartCode("SP-001");
        request.setQuantity(new BigDecimal("5"));
        BizException exception = assertThrows(BizException.class, () -> equipmentService.stockOut(request));
        assertTrue(exception.getMessage().contains("库存不足"));
        verify(sparePartStockMapper, never()).updateById(any(MesSparePartStock.class));
    }

    @Test
    @DisplayName("OEE 按默认公式计算")
    void shouldComputeOeeWithDefaultFormula() {
        MesEquipment equipment = new MesEquipment();
        equipment.setId(1L);
        equipment.setEquipmentCode("EQ-001");
        when(equipmentMapper.selectById(1L)).thenReturn(equipment);
        when(oeeSnapshotMapper.selectOne(any())).thenReturn(null);

        MesOeeSnapshotRequest request = new MesOeeSnapshotRequest();
        request.setEquipmentId(1L);
        request.setShiftDate(LocalDate.of(2026, 11, 5));
        request.setShiftCode("DAY");
        request.setPlannedMinutes(new BigDecimal("480"));
        request.setPlannedDowntimeMinutes(new BigDecimal("60"));
        request.setRunMinutes(new BigDecimal("360"));
        request.setTotalCount(new BigDecimal("360"));
        request.setGoodCount(new BigDecimal("342"));
        request.setIdealCycleSeconds(new BigDecimal("60"));

        MesOeeSnapshotVO vo = equipmentService.computeOee(request);
        assertEquals(new BigDecimal("0.857143"), vo.getAvailability());
        assertEquals(new BigDecimal("1.000000"), vo.getPerformance());
        assertEquals(new BigDecimal("0.950000"), vo.getQuality());
        assertEquals(new BigDecimal("0.814286"), vo.getOee());
    }
}
