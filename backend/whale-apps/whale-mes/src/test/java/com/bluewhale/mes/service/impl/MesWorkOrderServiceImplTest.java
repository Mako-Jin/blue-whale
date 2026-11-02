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
 * MES 生产工单服务单元测试（T-07 覆盖率补齐）。
 *
 * <p>覆盖分页参数归一化、编码唯一性校验、MapStruct 转换与逻辑存在的详情 / 更新 / 删除，
 * Mapper 以 Mock 隔离，不依赖 MySQL。</p>
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
     * 分页查询命中全部过滤条件，且每页条数上限截断为 200。
     */
    @Test
    void pageShouldApplyFiltersAndClampMaxSize() {
        MesWorkOrderQueryRequest request = new MesWorkOrderQueryRequest();
        request.setCode("WO");
        request.setName("装配");
        request.setStatus("ENABLED");
        request.setSize(500L);
        Page<MesWorkOrder> page = new Page<>(1, 200);
        page.setTotal(3L);
        page.setRecords(List.of(workOrder(1L)));
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
        when(mesWorkOrderMapper.selectById(1L)).thenReturn(workOrder(1L));

        MesWorkOrderVO view = mesWorkOrderService.getDetail(1L);

        assertEquals(1L, view.getId());
        assertEquals("WO-1", view.getCode());
        assertEquals("装配工单", view.getName());
        assertEquals("ENABLED", view.getStatus());
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
                () -> mesWorkOrderService.create(saveRequest("WO-1")));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(mesWorkOrderMapper, never()).insert(any(MesWorkOrder.class));
    }

    /**
     * 新增成功写入实体并回写数据库生成的主键与时间。
     */
    @Test
    void createShouldInsertAndReturnView() {
        when(mesWorkOrderMapper.selectCount(any())).thenReturn(0L);
        when(mesWorkOrderMapper.insert(any(MesWorkOrder.class))).thenAnswer(invocation -> {
            MesWorkOrder entity = invocation.getArgument(0);
            entity.setId(9L);
            entity.setCreateTime(FIXED_TIME);
            return 1;
        });

        MesWorkOrderVO view = mesWorkOrderService.create(saveRequest("WO-9"));

        assertEquals(9L, view.getId());
        assertEquals("WO-9", view.getCode());
        assertEquals("ENABLED", view.getStatus());
        assertEquals(FIXED_TIME, view.getCreateTime());

        ArgumentCaptor<MesWorkOrder> captor = ArgumentCaptor.forClass(MesWorkOrder.class);
        verify(mesWorkOrderMapper).insert(captor.capture());
        assertEquals("装配工单", captor.getValue().getName());
        assertEquals("首件检验", captor.getValue().getRemark());
    }

    /**
     * 更新时编码与其他工单冲突被拒绝。
     */
    @Test
    void updateShouldRejectDuplicatedCode() {
        when(mesWorkOrderMapper.selectById(1L)).thenReturn(workOrder(1L));
        when(mesWorkOrderMapper.selectCount(any())).thenReturn(2L);

        BizException exception = assertThrows(BizException.class,
                () -> mesWorkOrderService.update(1L, saveRequest("WO-2")));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(mesWorkOrderMapper, never()).updateById(any(MesWorkOrder.class));
    }

    /**
     * 更新成功覆盖既有实体字段后按主键写回。
     */
    @Test
    void updateShouldOverwriteEntityAndReturnView() {
        when(mesWorkOrderMapper.selectById(1L)).thenReturn(workOrder(1L));
        when(mesWorkOrderMapper.selectCount(any())).thenReturn(0L);

        MesWorkOrderVO view = mesWorkOrderService.update(1L, saveRequest("WO-1-NEW"));

        assertEquals("WO-1-NEW", view.getCode());
        assertEquals(1L, view.getId());
        verify(mesWorkOrderMapper).updateById(any(MesWorkOrder.class));
    }

    /**
     * 更新不存在的工单抛出 404。
     */
    @Test
    void updateShouldThrowWhenAbsent() {
        BizException exception = assertThrows(BizException.class,
                () -> mesWorkOrderService.update(2L, saveRequest("WO-2")));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 删除前校验存在性，成功后下发逻辑删除。
     */
    @Test
    void deleteShouldThrowWhenAbsentAndDelegateWhenPresent() {
        BizException exception = assertThrows(BizException.class, () -> mesWorkOrderService.delete(2L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());

        when(mesWorkOrderMapper.selectById(3L)).thenReturn(workOrder(3L));
        mesWorkOrderService.delete(3L);

        verify(mesWorkOrderMapper).deleteById(3L);
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
     * @param id 主键
     * @return 工单实体
     */
    private MesWorkOrder workOrder(Long id) {
        MesWorkOrder entity = new MesWorkOrder();
        entity.setId(id);
        entity.setCode("WO-1");
        entity.setName("装配工单");
        entity.setStatus("ENABLED");
        entity.setRemark("首件检验");
        entity.setCreateTime(FIXED_TIME);
        return entity;
    }

    /**
     * 构造工单保存请求。
     *
     * @param code 工单编码
     * @return 保存请求
     */
    private MesWorkOrderSaveRequest saveRequest(String code) {
        MesWorkOrderSaveRequest request = new MesWorkOrderSaveRequest();
        request.setCode(code);
        request.setName("装配工单");
        request.setStatus("ENABLED");
        request.setRemark("首件检验");
        return request;
    }
}
