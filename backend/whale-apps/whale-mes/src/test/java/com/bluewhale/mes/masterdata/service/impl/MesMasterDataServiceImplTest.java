package com.bluewhale.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.masterdata.converter.MesMasterDataConverter;
import com.bluewhale.mes.masterdata.dto.MesMasterDataQueryRequest;
import com.bluewhale.mes.masterdata.dto.MesMasterDataSaveRequest;
import com.bluewhale.mes.masterdata.entity.MesMasterDataEntity;
import com.bluewhale.mes.masterdata.entity.MesProductionLine;
import com.bluewhale.mes.masterdata.entity.MesShift;
import com.bluewhale.mes.masterdata.entity.MesWorkshop;
import com.bluewhale.mes.masterdata.enums.MasterDataKind;
import com.bluewhale.mes.masterdata.mapper.MesDefectCodeMapper;
import com.bluewhale.mes.masterdata.mapper.MesOperationMapper;
import com.bluewhale.mes.masterdata.mapper.MesProductionLineMapper;
import com.bluewhale.mes.masterdata.mapper.MesReasonCodeMapper;
import com.bluewhale.mes.masterdata.mapper.MesShiftMapper;
import com.bluewhale.mes.masterdata.mapper.MesStationMapper;
import com.bluewhale.mes.masterdata.mapper.MesTeamMapper;
import com.bluewhale.mes.masterdata.mapper.MesWorkCenterMapper;
import com.bluewhale.mes.masterdata.mapper.MesWorkshopMapper;
import com.bluewhale.mes.masterdata.vo.MesMasterDataVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
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
 * MES 车间主数据服务单元测试。
 *
 * <p>主数据九种类型共用一套服务实现，测试以车间 / 产线 / 班次为代表验证分页、唯一性、
 * 上下级引用校验、删除引用保护与选项查询；Mapper 与 MapStruct 转换器均以 Mock 隔离。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesMasterDataServiceImplTest {

    @Mock
    private MesWorkshopMapper workshopMapper;

    @Mock
    private MesProductionLineMapper lineMapper;

    @Mock
    private MesWorkCenterMapper workCenterMapper;

    @Mock
    private MesStationMapper stationMapper;

    @Mock
    private MesTeamMapper teamMapper;

    @Mock
    private MesShiftMapper shiftMapper;

    @Mock
    private MesOperationMapper operationMapper;

    @Mock
    private MesDefectCodeMapper defectCodeMapper;

    @Mock
    private MesReasonCodeMapper reasonCodeMapper;

    @Mock
    private MesMasterDataConverter converter;

    @InjectMocks
    private MesMasterDataServiceImpl mesMasterDataService;

    /**
     * 分页查询按类型路由到对应 Mapper，并映射为共用视图。
     *
     * <p>车间表自身没有 {@code workshop_id} 列，传入车间维度条件必须被忽略。</p>
     */
    @Test
    void pageShouldRouteToMapperAndMapRecords() {
        MesMasterDataQueryRequest request = new MesMasterDataQueryRequest();
        request.setCode("WS");
        request.setWorkshopId(101L);
        request.setSize(500L);
        Page<MesWorkshop> page = new Page<>(1, 200);
        page.setTotal(1L);
        page.setRecords(List.of(workshop(101L)));
        when(workshopMapper.selectPage(any(), any())).thenReturn(page);
        when(converter.toVo(any(MesMasterDataEntity.class))).thenReturn(workshopView());

        PageResult<MesMasterDataVO> result = mesMasterDataService.page(MasterDataKind.WORKSHOP, request);

        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
        assertEquals(200L, result.getSize());

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(workshopMapper).selectPage(any(), captor.capture());
        assertFalse(captor.getValue().getSqlSegment().contains("workshop_id"));
    }

    /**
     * 产线分页按车间维度过滤，SQL 条件包含 {@code workshop_id}。
     */
    @Test
    void pageShouldFilterLineByWorkshop() {
        MesMasterDataQueryRequest request = new MesMasterDataQueryRequest();
        request.setWorkshopId(101L);
        Page<MesProductionLine> page = new Page<>(1, 200);
        page.setRecords(List.of(new MesProductionLine()));
        when(lineMapper.selectPage(any(), any())).thenReturn(page);
        when(converter.toVo(any(MesMasterDataEntity.class))).thenReturn(workshopView());

        mesMasterDataService.page(MasterDataKind.LINE, request);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(lineMapper).selectPage(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("workshop_id"));
    }

    /**
     * 详情不存在时抛出 404。
     */
    @Test
    void detailShouldThrowWhenAbsent() {
        BizException exception = assertThrows(BizException.class,
                () -> mesMasterDataService.detail(MasterDataKind.WORKSHOP, 9L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(workshopMapper).selectById(9L);
    }

    /**
     * 新增时编码重复被拒绝，且不写库。
     */
    @Test
    void createShouldRejectDuplicatedCode() {
        when(workshopMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> mesMasterDataService.create(MasterDataKind.WORKSHOP, saveRequest()));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(workshopMapper, never()).insert(any(MesWorkshop.class));
    }

    /**
     * 新增产线未选择所属车间被拒绝。
     */
    @Test
    void createLineShouldRequireWorkshop() {
        when(lineMapper.selectCount(any())).thenReturn(0L);
        MesMasterDataSaveRequest request = saveRequest();
        request.setWorkshopId(null);

        BizException exception = assertThrows(BizException.class,
                () -> mesMasterDataService.create(MasterDataKind.LINE, request));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(lineMapper, never()).insert(any(MesProductionLine.class));
    }

    /**
     * 新增产线引用不存在的车间抛出 404。
     */
    @Test
    void createLineShouldRejectMissingWorkshop() {
        when(lineMapper.selectCount(any())).thenReturn(0L);
        when(workshopMapper.selectById(101L)).thenReturn(null);
        MesMasterDataSaveRequest request = saveRequest();
        request.setWorkshopId(101L);

        BizException exception = assertThrows(BizException.class,
                () -> mesMasterDataService.create(MasterDataKind.LINE, request));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(lineMapper, never()).insert(any(MesProductionLine.class));
    }

    /**
     * 删除被下级引用的车间被拒绝。
     */
    @Test
    void deleteShouldRejectReferencedWorkshop() {
        when(workshopMapper.selectById(101L)).thenReturn(workshop(101L));
        when(lineMapper.selectCount(any())).thenReturn(1L);
        when(workCenterMapper.selectCount(any())).thenReturn(0L);
        when(teamMapper.selectCount(any())).thenReturn(0L);

        BizException exception = assertThrows(BizException.class,
                () -> mesMasterDataService.delete(MasterDataKind.WORKSHOP, 101L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(workshopMapper, never()).deleteById(101L);
    }

    /**
     * 删除未被引用的车间成功下发逻辑删除。
     */
    @Test
    void deleteShouldRemoveWhenNotReferenced() {
        when(workshopMapper.selectById(101L)).thenReturn(workshop(101L));
        when(lineMapper.selectCount(any())).thenReturn(0L);
        when(workCenterMapper.selectCount(any())).thenReturn(0L);
        when(teamMapper.selectCount(any())).thenReturn(0L);

        mesMasterDataService.delete(MasterDataKind.WORKSHOP, 101L);

        verify(workshopMapper).deleteById(101L);
    }

    /**
     * 班次开始时间与结束时间相同被拒绝。
     */
    @Test
    void createShiftShouldRejectSameStartAndEndTime() {
        when(shiftMapper.selectCount(any())).thenReturn(0L);
        MesMasterDataSaveRequest request = saveRequest();
        request.setStartTime(LocalTime.of(8, 0));
        request.setEndTime(LocalTime.of(8, 0));

        BizException exception = assertThrows(BizException.class,
                () -> mesMasterDataService.create(MasterDataKind.SHIFT, request));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(shiftMapper, never()).insert(any(MesShift.class));
    }

    /**
     * 选项接口仅返回启用状态记录。
     */
    @Test
    void optionsShouldReturnEnabledRecords() {
        MesShift shift = new MesShift();
        shift.setId(601L);
        shift.setCode("SH01");
        shift.setName("白班");
        shift.setStatus("ENABLED");
        when(shiftMapper.selectList(any())).thenReturn(List.of(shift));
        when(converter.toVo(any(MesMasterDataEntity.class))).thenReturn(workshopView());

        List<MesMasterDataVO> options = mesMasterDataService.options(MasterDataKind.SHIFT);

        assertEquals(1, options.size());
        verify(shiftMapper).selectList(any());
    }

    /**
     * 构造车间实体。
     *
     * @param id 主键
     * @return 车间实体
     */
    private MesWorkshop workshop(Long id) {
        MesWorkshop entity = new MesWorkshop();
        entity.setId(id);
        entity.setCode("WS01");
        entity.setName("总装车间");
        entity.setStatus("ENABLED");
        return entity;
    }

    /**
     * 构造车间视图。
     *
     * @return 车间视图
     */
    private MesMasterDataVO workshopView() {
        MesMasterDataVO view = new MesMasterDataVO();
        view.setId(101L);
        view.setCode("WS01");
        view.setName("总装车间");
        view.setStatus("ENABLED");
        return view;
    }

    /**
     * 构造主数据保存请求。
     *
     * @return 保存请求
     */
    private MesMasterDataSaveRequest saveRequest() {
        MesMasterDataSaveRequest request = new MesMasterDataSaveRequest();
        request.setCode("WS02");
        request.setName("新车间");
        request.setStatus("ENABLED");
        return request;
    }
}
