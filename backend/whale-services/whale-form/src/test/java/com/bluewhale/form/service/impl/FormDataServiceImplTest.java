package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormDataQueryRequest;
import com.bluewhale.form.dto.FormDataSubmitRequest;
import com.bluewhale.form.entity.FormData;
import com.bluewhale.form.entity.FormDefinition;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.vo.FormDataVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormDataServiceImpl} 表单数据提交 / 详情 / 删除 / 分页单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormDataServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    @Mock
    private FormDataMapper formDataMapper;

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @InjectMocks
    private FormDataServiceImpl formDataService;

    /**
     * 构造表单定义桩对象。
     *
     * @param id 表单定义ID
     * @return 表单定义实体
     */
    private static FormDefinition definition(Long id) {
        FormDefinition definition = new FormDefinition();
        definition.setId(id);
        definition.setCode("fm-a");
        definition.setVersion(1);
        return definition;
    }

    /**
     * 构造提交请求桩对象。
     *
     * @param definitionId 表单定义ID
     * @param dataJson 表单数据JSON
     * @return 提交请求
     */
    private static FormDataSubmitRequest submitRequest(Long definitionId, String dataJson) {
        FormDataSubmitRequest request = new FormDataSubmitRequest();
        request.setDefinitionId(definitionId);
        request.setDataJson(dataJson);
        request.setBizKey("BK-1");
        return request;
    }

    @Test
    @DisplayName("submit：定义存在且数据合法时落库并回填定义冗余字段")
    void shouldSubmitWhenDefinitionValid() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));

        FormDataVO vo = formDataService.submit(submitRequest(10L, "{\"name\":\"张三\"}"), "lisi");

        ArgumentCaptor<FormData> captor = ArgumentCaptor.forClass(FormData.class);
        verify(formDataMapper).insert(captor.capture());
        assertEquals(10L, captor.getValue().getDefinitionId());
        assertEquals("fm-a", captor.getValue().getDefinitionCode());
        assertEquals(1, captor.getValue().getDefinitionVersion().intValue());
        assertEquals("BK-1", captor.getValue().getBizKey());
        assertEquals("{\"name\":\"张三\"}", captor.getValue().getDataJson());
        assertEquals("lisi", captor.getValue().getSubmitter());
        assertEquals(1, captor.getValue().getStatus().intValue());
        assertEquals("fm-a", vo.getDefinitionCode());
    }

    @Test
    @DisplayName("submit：定义不存在抛 NOT_FOUND 且不落库")
    void shouldThrowWhenSubmitDefinitionMissing() {
        when(formDefinitionMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(99L, "{}"), "lisi"));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(formDataMapper, never()).insert(any(FormData.class));
    }

    @Test
    @DisplayName("submit：数据超过 64KB 抛参数错误且不落库")
    void shouldThrowWhenSubmitDataTooLong() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        String longData = "a".repeat(65537);

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, longData), "lisi"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("表单数据长度不能超过 65536 字符", ex.getMessage());
        verify(formDataMapper, never()).insert(any(FormData.class));
    }

    @Test
    @DisplayName("getDetail：返回表单数据详情")
    void shouldGetDetail() {
        FormData entity = new FormData();
        entity.setId(1L);
        entity.setDefinitionCode("fm-a");
        when(formDataMapper.selectById(1L)).thenReturn(entity);

        FormDataVO vo = formDataService.getDetail(1L);

        assertEquals(1L, vo.getId());
        assertEquals("fm-a", vo.getDefinitionCode());
    }

    @Test
    @DisplayName("getDetail：数据不存在抛 NOT_FOUND")
    void shouldThrowWhenDetailMissing() {
        when(formDataMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> formDataService.getDetail(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("delete：存在则逻辑删除")
    void shouldDeleteWhenExists() {
        FormData entity = new FormData();
        entity.setId(7L);
        when(formDataMapper.selectById(7L)).thenReturn(entity);

        formDataService.delete(7L);

        verify(formDataMapper).deleteById(7L);
    }

    @Test
    @DisplayName("delete：数据不存在抛 NOT_FOUND 且不删除")
    void shouldThrowWhenDeleteMissing() {
        when(formDataMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> formDataService.delete(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(formDataMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("page：按条件调用分页查询并映射结果")
    void shouldPageWithConditions() {
        FormData entity = new FormData();
        entity.setId(1L);
        entity.setSubmitter("lisi");
        Page<FormData> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(formDataMapper.selectPage(ArgumentMatchers.<Page<FormData>>any(),
                ArgumentMatchers.<Wrapper<FormData>>any())).thenReturn(page);
        FormDataQueryRequest request = new FormDataQueryRequest();
        request.setDefinitionCode("fm-a");
        request.setBizKey("BK-1");
        request.setSubmitter("lisi");

        PageResult<FormDataVO> result = formDataService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("lisi", result.getRecords().get(0).getSubmitter());
    }
}
