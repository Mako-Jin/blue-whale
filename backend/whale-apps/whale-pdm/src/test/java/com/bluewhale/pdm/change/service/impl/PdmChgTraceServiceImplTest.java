package com.bluewhale.pdm.change.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pdm.change.dto.PdmChgTraceQueryRequest;
import com.bluewhale.pdm.change.entity.PdmChgTrace;
import com.bluewhale.pdm.change.mapper.PdmChgTraceMapper;
import com.bluewhale.pdm.change.vo.PdmChgTraceVO;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 变更追溯服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmChgTraceServiceImplTest {

    @Mock
    private PdmChgTraceMapper traceMapper;

    @InjectMocks
    private PdmChgTraceServiceImpl traceService;

    /**
     * 初始化 MyBatis-Plus 表元数据。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PdmChgTrace.class);
    }

    /**
     * 按对象时间线返回追溯记录。
     */
    @Test
    void timelineShouldReturnRecords() {
        PdmChgTrace trace = new PdmChgTrace();
        trace.setId(1L);
        trace.setBizType("ECO");
        trace.setObjectType("BOM");
        trace.setObjectId(100L);
        trace.setAction("EFFECTIVE");
        trace.setTraceTime(LocalDateTime.now());
        when(traceMapper.selectList(any())).thenReturn(List.of(trace));

        List<PdmChgTraceVO> timeline = traceService.timeline("bom", 100L);

        assertEquals(1, timeline.size());
        assertEquals("BOM", timeline.get(0).getObjectType());
        verify(traceMapper).selectList(any());
    }

    /**
     * 对象类型或对象ID为空报参数错误。
     */
    @Test
    void timelineShouldRejectBlankParams() {
        assertThrows(BizException.class, () -> traceService.timeline(" ", 100L));
    }

    /**
     * 分页条数超过上限被收窄到 200。
     */
    @Test
    void pageShouldClampSize() {
        Page<PdmChgTrace> page = new Page<>(1L, 200L);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(traceMapper.selectPage(any(), any())).thenReturn(page);

        PdmChgTraceQueryRequest request = new PdmChgTraceQueryRequest();
        request.setSize(5000L);
        traceService.page(request);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Page> captor = ArgumentCaptor.forClass(Page.class);
        verify(traceMapper).selectPage(captor.capture(), any());
        assertEquals(200L, captor.getValue().getSize());
    }
}
