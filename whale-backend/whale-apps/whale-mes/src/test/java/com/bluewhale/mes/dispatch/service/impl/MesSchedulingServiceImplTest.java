package com.bluewhale.mes.dispatch.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.dispatch.dto.MesScheduleActionRequest;
import com.bluewhale.mes.dispatch.dto.MesScheduleGenerateRequest;
import com.bluewhale.mes.dispatch.entity.MesSchedulePlan;
import com.bluewhale.mes.dispatch.entity.MesScheduleSlot;
import com.bluewhale.mes.dispatch.entity.MesWorkOrderTask;
import com.bluewhale.mes.dispatch.mapper.MesSchedulePlanMapper;
import com.bluewhale.mes.dispatch.mapper.MesScheduleSlotMapper;
import com.bluewhale.mes.dispatch.mapper.MesWorkOrderTaskMapper;
import com.bluewhale.mes.dispatch.vo.MesScheduleGenerateVO;
import com.bluewhale.mes.dispatch.vo.MesSchedulePlanVO;
import com.bluewhale.mes.masterdata.entity.MesShift;
import com.bluewhale.mes.masterdata.entity.MesWorkCenter;
import com.bluewhale.mes.masterdata.mapper.MesShiftMapper;
import com.bluewhale.mes.masterdata.mapper.MesWorkCenterMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 有限产能排程服务单元测试（Step 6a）。
 *
 * <p>覆盖排程生成按班次产能共享分配、缺班次拒绝，以及锁定/取消状态机。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesSchedulingServiceImplTest {

    /** 测试工作中心 ID */
    private static final Long CENTER_ID = 21L;

    /** 测试工单 ID */
    private static final Long WORK_ORDER_ID = 31L;

    @Mock
    private MesSchedulePlanMapper planMapper;

    @Mock
    private MesScheduleSlotMapper slotMapper;

    @Mock
    private MesWorkOrderTaskMapper taskMapper;

    @Mock
    private MesWorkCenterMapper workCenterMapper;

    @Mock
    private MesShiftMapper shiftMapper;

    @InjectMocks
    private MesSchedulingServiceImpl schedulingService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, MesSchedulePlan.class);
        TableInfoHelper.initTableInfo(assistant, MesScheduleSlot.class);
        TableInfoHelper.initTableInfo(assistant, MesWorkOrderTask.class);
        TableInfoHelper.initTableInfo(assistant, MesWorkCenter.class);
        TableInfoHelper.initTableInfo(assistant, MesShift.class);
    }

    /**
     * 生成排程：同工作中心日产能被多个任务共享，超产能顺延次日
     */
    @Test
    @DisplayName("排程生成：日产能共享并顺延")
    void generateShouldShareDailyCapacity() {
        when(shiftMapper.selectOne(any())).thenReturn(shift());
        when(workCenterMapper.selectList(any())).thenReturn(List.of(center("10")));
        when(slotMapper.selectList(any())).thenReturn(List.of());
        MesWorkOrderTask first = task(1L, "15");
        MesWorkOrderTask second = task(2L, "15");
        when(taskMapper.selectList(any())).thenReturn(List.of(second, first));
        when(planMapper.insert(any(MesSchedulePlan.class))).thenReturn(1);
        when(slotMapper.insert(any(MesScheduleSlot.class))).thenReturn(1);

        MesScheduleGenerateVO result = schedulingService.generate(request(3));

        assertEquals(1, result.getPlanCount());
        assertEquals(4, result.getSlotCount());
        assertEquals(0, result.getUnscheduledTasks());
        ArgumentCaptor<MesScheduleSlot> captor = ArgumentCaptor.forClass(MesScheduleSlot.class);
        verify(slotMapper, times(4)).insert(captor.capture());
        List<MesScheduleSlot> slots = captor.getAllValues();
        assertEquals(0, new BigDecimal("10").compareTo(slots.get(0).getAllocatedQuantity()));
        assertEquals(0, new BigDecimal("5").compareTo(slots.get(1).getAllocatedQuantity()));
        assertEquals(0, new BigDecimal("5").compareTo(slots.get(2).getAllocatedQuantity()));
        assertEquals(0, new BigDecimal("10").compareTo(slots.get(3).getAllocatedQuantity()));
    }

    /**
     * 排程生成：未配置班次拒绝
     */
    @Test
    @DisplayName("排程生成：缺少班次拒绝")
    void generateShouldRejectWhenNoShift() {
        when(shiftMapper.selectOne(any())).thenReturn(null);
        MesScheduleGenerateRequest request = request(7);

        BizException exception = assertThrows(BizException.class,
                () -> schedulingService.generate(request));

        assertEquals("未配置可用班次，无法生成排程", exception.getMessage());
    }

    /**
     * 锁定：草稿 → 已锁定
     */
    @Test
    @DisplayName("排程锁定：草稿转已锁定")
    void lockShouldTransitionDraft() {
        MesSchedulePlan plan = plan("DRAFT");
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(planMapper.updateById(any(MesSchedulePlan.class))).thenReturn(1);

        MesSchedulePlanVO result = schedulingService.lock(1L, new MesScheduleActionRequest());

        assertEquals("LOCKED", result.getStatus());
        assertEquals("LOCKED", plan.getStatus());
    }

    /**
     * 取消：草稿 → 已取消并释放时段
     */
    @Test
    @DisplayName("排程取消：释放时段")
    void cancelShouldReleaseSlots() {
        MesSchedulePlan plan = plan("DRAFT");
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(planMapper.updateById(any(MesSchedulePlan.class))).thenReturn(1);
        MesScheduleSlot slot = new MesScheduleSlot();
        slot.setId(9L);
        slot.setPlanId(1L);
        slot.setStatus("RESERVED");
        when(slotMapper.selectList(any())).thenReturn(List.of(slot));
        when(slotMapper.updateById(any(MesScheduleSlot.class))).thenReturn(1);

        schedulingService.cancel(1L, new MesScheduleActionRequest());

        assertEquals("RELEASED", slot.getStatus());
        verify(slotMapper, times(1)).updateById(slot);
    }

    /**
     * 构造排程请求
     *
     * @param days 天数
     * @return 请求
     */
    private MesScheduleGenerateRequest request(int days) {
        MesScheduleGenerateRequest request = new MesScheduleGenerateRequest();
        request.setPlanDate(LocalDate.of(2026, 11, 9));
        request.setDays(days);
        return request;
    }

    /**
     * 构造启用班次
     *
     * @return 班次
     */
    private MesShift shift() {
        MesShift shift = new MesShift();
        shift.setId(5L);
        shift.setStatus("ENABLED");
        return shift;
    }

    /**
     * 构造启用工作中心
     *
     * @param capacity 每班产能
     * @return 工作中心
     */
    private MesWorkCenter center(String capacity) {
        MesWorkCenter center = new MesWorkCenter();
        center.setId(CENTER_ID);
        center.setCode("WC-01");
        center.setStatus("ENABLED");
        center.setCapacityPerShift(new BigDecimal(capacity));
        return center;
    }

    /**
     * 构造待排任务
     *
     * @param id   任务 ID
     * @param qty  计划数量
     * @return 任务
     */
    private MesWorkOrderTask task(Long id, String qty) {
        MesWorkOrderTask task = new MesWorkOrderTask();
        task.setId(id);
        task.setTaskNo("T-" + id);
        task.setWorkOrderId(WORK_ORDER_ID);
        task.setWorkOrderCode("WO-01");
        task.setWorkCenterId(CENTER_ID);
        task.setWorkCenterCode("WC-01");
        task.setOperationCode("OP-01");
        task.setOperationName("装配");
        task.setStatus("DISPATCHED");
        task.setPriority("NORMAL");
        task.setPlannedQuantity(new BigDecimal(qty));
        task.setCompletedQuantity(BigDecimal.ZERO);
        return task;
    }

    /**
     * 构造排程计划
     *
     * @param status 状态
     * @return 计划
     */
    private MesSchedulePlan plan(String status) {
        MesSchedulePlan plan = new MesSchedulePlan();
        plan.setId(1L);
        plan.setPlanNo("SCH-001");
        plan.setStatus(status);
        return plan;
    }
}
