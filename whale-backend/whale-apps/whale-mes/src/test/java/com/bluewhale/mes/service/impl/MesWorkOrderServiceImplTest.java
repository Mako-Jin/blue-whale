package com.bluewhale.mes.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.dto.MesWorkOrderQueryRequest;
import com.bluewhale.mes.dto.MesWorkOrderSaveRequest;
import com.bluewhale.mes.entity.MesWorkOrder;
import com.bluewhale.mes.mapper.MesWorkOrderMapper;
import com.bluewhale.mes.vo.MesWorkOrderVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 生产工单服务单元测试（M0 工单生命周期升级）。
 *
 * <p>覆盖分页参数归一化、编码 / ERP 单号唯一性、状态机迁移、计划时间校验、MapStruct
 * 转换与删除限制，Mapper 以 Mock 隔离，不依赖 MySQL。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesWorkOrderServiceImplTest {

    /** 固定时间，便于断言生成主键回填 */
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock
    private MesWorkOrderMapper mesWorkOrderMapper;

    @InjectMocks
    private MesWorkOrderServiceImpl mesWorkOrderService;

    /**
     * 初始化 MyBatis-Plus 表元数据，供 Lambda 查询条件解析列名。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MesWorkOrder.class);
    }

    /**
     * 分页查询命中过滤条件，且每页条数上限截断为 200。
     */
    @Test
    void pageShouldApplyFiltersAndClampMaxSize() {
        MesWorkOrderQueryRequest request = new MesWorkOrderQueryRequest();
        request.setCode("WO");
        request.setErpOrderNo("PO");
        request.setItemCode("ITEM");
        request.setStatus("IN_PROGRESS");
        request.setPriority("URGENT");
        request.setSize(500L);
        Page<MesWorkOrder> page = new Page<>(1, 200);
        page.setTotal(3L);
        page.setRecords(List.of(workOrder(1L, "IN_PROGRESS")));
        when(mesWorkOrderMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<MesWorkOrderVO> result = mesWorkOrderService.page(request);

        assertEquals(1, result.getRecords().size());
        assertEquals(3L, result.getTotal());
        assertEquals(200L, result.getSize());
    }

    /**
     * 分页查询每页条数下限收敛为 1，避免非法值。
     */
    @Test
    void pageShouldClampMinSize() {
        MesWorkOrderQueryRequest request = new MesWorkOrderQueryRequest();
        request.setSize(0L);
        Page<MesWorkOrder> page = new Page<>(1, 1);
        page.setRecords(List.of());
        when(mesWorkOrderMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<MesWorkOrderVO> result = mesWorkOrderService.page(request);

        assertEquals(1L, result.getSize());
        assertEquals(0, result.getRecords().size());
    }

    /**
     * 详情按 ID 查询并通过 MapStruct 映射为视图。
     */
    @Test
    void getDetailShouldMapEntity() {
        when(mesWorkOrderMapper.selectById(1L)).thenReturn(workOrder(1L, "RECEIVED"));

        MesWorkOrderVO view = mesWorkOrderService.getDetail(1L);

        assertEquals(1L, view.getId());
        assertEquals("WO-1", view.getCode());
        assertEquals("装配工单", view.getName());
        assertEquals("RECEIVED", view.getStatus());
        assertEquals("PO-1", view.getErpOrderNo());
    }

    /**
     * 详情记录不存在抛出 404。
     */
    @Test
    void getDetailShouldThrowWhenAbsent() {
        BizException exception = assertThrows(BizException.class, () -> mesWorkOrderService.getDetail(2L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 新增时编码重复被拒绝，且不写库。
     */
    @Test
    void createShouldRejectDuplicatedCode() {
        when(mesWorkOrderMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> mesWorkOrderService.create(saveRequest("WO-1", null)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(mesWorkOrderMapper, never()).insert(any(MesWorkOrder.class));
    }

    /**
     * 新增时 ERP 生产订单号重复被拒绝，且不写库。
     */
    @Test
    void createShouldRejectDuplicatedErpOrderNo() {
        when(mesWorkOrderMapper.selectCount(any())).thenReturn(0L, 1L);
        MesWorkOrderSaveRequest request = saveRequest("WO-2", null);
        request.setErpOrderNo("PO-2");

        BizException exception = assertThrows(BizException.class,
                () -> mesWorkOrderService.create(request));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(mesWorkOrderMapper, never()).insert(any(MesWorkOrder.class));
    }

    /**
     * 新增未指定状态时默认 RECEIVED，并初始化优先级与乐观锁版本号。
     */
    @Test
    void createShouldDefaultStatusAndReturnView() {
        when(mesWorkOrderMapper.selectCount(any())).thenReturn(0L);
        when(mesWorkOrderMapper.insert(any(MesWorkOrder.class))).thenAnswer(invocation -> {
            MesWorkOrder entity = invocation.getArgument(0);
            entity.setId(9L);
            entity.setCreateTime(FIXED_TIME);
            return 1;
        });

        MesWorkOrderVO view = mesWorkOrderService.create(saveRequest("WO-9", null));

        assertEquals(9L, view.getId());
        assertEquals("WO-9", view.getCode());
        assertEquals("RECEIVED", view.getStatus());
        assertEquals("NORMAL", view.getPriority());
        assertEquals(1, view.getVersion());
        assertEquals(FIXED_TIME, view.getCreateTime());

        ArgumentCaptor<MesWorkOrder> captor = ArgumentCaptor.forClass(MesWorkOrder.class);
        verify(mesWorkOrderMapper).insert(captor.capture());
        assertEquals("装配工单", captor.getValue().getName());
    }

    /**
     * 新增时计划完工早于计划开工被拒绝。
     */
    @Test
    void createShouldRejectInvalidPlanTime() {
        when(mesWorkOrderMapper.selectCount(any())).thenReturn(0L);
        MesWorkOrderSaveRequest request = saveRequest("WO-3", null);
        request.setPlannedStart(LocalDateTime.of(2026, 11, 5, 8, 0));
        request.setPlannedEnd(LocalDateTime.of(2026, 11, 4, 8, 0));

        BizException exception = assertThrows(BizException.class,
                () -> mesWorkOrderService.create(request));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(mesWorkOrderMapper, never()).insert(any(MesWorkOrder.class));
    }

    /**
     * 更新时编码与其他工单冲突被拒绝。
     */
    @Test
    void updateShouldRejectDuplicatedCode() {
        when(mesWorkOrderMapper.selectById(1L)).thenReturn(workOrder(1L, "RECEIVED"));
        when(mesWorkOrderMapper.selectCount(any())).thenReturn(2L);

        BizException exception = assertThrows(BizException.class,
                () -> mesWorkOrderService.update(1L, saveRequest("WO-2", "RECEIVED")));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(mesWorkOrderMapper, never()).updateById(any(MesWorkOrder.class));
    }

    /**
     * 更新时非法状态迁移（已关闭 → 生产中）被拒绝。
     */
    @Test
    void updateShouldRejectIllegalTransition() {
        when(mesWorkOrderMapper.selectById(1L)).thenReturn(workOrder(1L, "CLOSED"));

        BizException exception = assertThrows(BizException.class,
                () -> mesWorkOrderService.update(1L, saveRequest("WO-1", "IN_PROGRESS")));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(mesWorkOrderMapper, never()).updateById(any(MesWorkOrder.class));
    }

    /**
     * 更新允许合法迁移：生产中 → 已完工。
     */
    @Test
    void updateShouldAcceptLegalTransition() {
        when(mesWorkOrderMapper.selectById(1L)).thenReturn(workOrder(1L, "IN_PROGRESS"));
        when(mesWorkOrderMapper.selectCount(any())).thenReturn(0L);
        when(mesWorkOrderMapper.updateById(any(MesWorkOrder.class))).thenReturn(1);

        MesWorkOrderVO view = mesWorkOrderService.update(1L, saveRequest("WO-1", "COMPLETED"));

        assertEquals("COMPLETED", view.getStatus());
        assertEquals(1L, view.getId());
        verify(mesWorkOrderMapper).updateById(any(MesWorkOrder.class));
    }

    /**
     * 更新不传状态时保留当前状态。
     */
    @Test
    void updateShouldKeepStatusWhenOmitted() {
        when(mesWorkOrderMapper.selectById(1L)).thenReturn(workOrder(1L, "PAUSED"));
        when(mesWorkOrderMapper.selectCount(any())).thenReturn(0L);
        when(mesWorkOrderMapper.updateById(any(MesWorkOrder.class))).thenReturn(1);

        MesWorkOrderVO view = mesWorkOrderService.update(1L, saveRequest("WO-1-NEW", null));

        assertEquals("PAUSED", view.getStatus());
        assertEquals("WO-1-NEW", view.getCode());
    }

    /**
     * 乐观锁冲突（更新影响 0 行）时提示刷新重试。
     */
    @Test
    void updateShouldRejectConcurrentModification() {
        when(mesWorkOrderMapper.selectById(1L)).thenReturn(workOrder(1L, "RECEIVED"));
        when(mesWorkOrderMapper.selectCount(any())).thenReturn(0L);
        when(mesWorkOrderMapper.updateById(any(MesWorkOrder.class))).thenReturn(0);

        BizException exception = assertThrows(BizException.class,
                () -> mesWorkOrderService.update(1L, saveRequest("WO-1", "DISPATCHED")));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    /**
     * 更新不存在的工单抛出 404。
     */
    @Test
    void updateShouldThrowWhenAbsent() {
        BizException exception = assertThrows(BizException.class,
                () -> mesWorkOrderService.update(2L, saveRequest("WO-2", "RECEIVED")));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 删除前校验存在性，已接收工单允许逻辑删除。
     */
    @Test
    void deleteShouldThrowWhenAbsentAndDelegateWhenPresent() {
        BizException exception = assertThrows(BizException.class, () -> mesWorkOrderService.delete(2L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());

        when(mesWorkOrderMapper.selectById(3L)).thenReturn(workOrder(3L, "RECEIVED"));
        mesWorkOrderService.delete(3L);

        verify(mesWorkOrderMapper).deleteById(3L);
    }

    /**
     * 已投产工单不允许删除，避免执行数据悬空。
     */
    @Test
    void deleteShouldRejectNonTerminalStartState() {
        when(mesWorkOrderMapper.selectById(4L)).thenReturn(workOrder(4L, "IN_PROGRESS"));

        BizException exception = assertThrows(BizException.class, () -> mesWorkOrderService.delete(4L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(mesWorkOrderMapper, never()).deleteById(any(Long.class));
    }

    /**
     * 主键为空时直接判定不存在（不触达数据库）。
     */
    @Test
    void requireWorkOrderShouldRejectNullId() {
        BizException exception = assertThrows(BizException.class, () -> mesWorkOrderService.getDetail(null));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(mesWorkOrderMapper, never()).selectById(any());
    }

    /**
     * 构造工单实体。
     *
     * @param id     主键
     * @param status 生命周期状态
     * @return 工单实体
     */
    private MesWorkOrder workOrder(Long id, String status) {
        MesWorkOrder entity = new MesWorkOrder();
        entity.setId(id);
        entity.setCode("WO-1");
        entity.setName("装配工单");
        entity.setStatus(status);
        entity.setErpOrderNo("PO-1");
        entity.setPriority("NORMAL");
        entity.setVersion(1);
        entity.setRemark("首件检验");
        entity.setCreateTime(FIXED_TIME);
        return entity;
    }

    /**
     * 构造工单保存请求。
     *
     * @param code   工单编码
     * @param status 目标状态，为空表示不变更
     * @return 保存请求
     */
    private MesWorkOrderSaveRequest saveRequest(String code, String status) {
        MesWorkOrderSaveRequest request = new MesWorkOrderSaveRequest();
        request.setCode(code);
        request.setName("装配工单");
        request.setStatus(status);
        request.setRemark("首件检验");
        return request;
    }
}
