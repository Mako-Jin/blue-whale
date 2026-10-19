package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormDataQueryRequest;
import com.bluewhale.form.dto.FormDataSubmitRequest;
import com.bluewhale.form.entity.FormData;
import com.bluewhale.form.entity.FormDefinition;
import com.bluewhale.form.entity.FormTableRegistry;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.support.FormDataEngine;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormDataServiceImpl} 表单数据提交 / 详情 / 删除 / 分页单元测试（主从分离双写 + 租户隔离）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormDataServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;
    private static final long TENANT = 1L;
    private static final String CODE = "fm_a";
    private static final String TABLE = "form_data_fm_a_v1";

    @Mock
    private FormDataMapper formDataMapper;

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @Mock
    private FormTableRegistryMapper formTableRegistryMapper;

    @Mock
    private FormDataEngine formDataEngine;

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
        definition.setCode(CODE);
        definition.setVersion(1);
        return definition;
    }

    /**
     * 构造物理表登记桩对象。
     *
     * @return 登记实体
     */
    private static FormTableRegistry registry() {
        FormTableRegistry registry = new FormTableRegistry();
        registry.setDefinitionId(10L);
        registry.setDefinitionCode(CODE);
        registry.setDefinitionVersion(1);
        registry.setPhysicalTable(TABLE);
        registry.setSchemaJson("{\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\",\"required\":true}]}");
        return registry;
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
    @DisplayName("submit：定义存在且版本已发布时双写主表与物理表")
    void shouldSubmitWhenPublished() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistry>>any()))
                .thenReturn(registry());

        FormDataVO vo = formDataService.submit(submitRequest(10L, "{\"name\":\"张三\"}"), "lisi", TENANT);

        ArgumentCaptor<FormData> captor = ArgumentCaptor.forClass(FormData.class);
        verify(formDataMapper).insert(captor.capture());
        assertEquals(TENANT, captor.getValue().getTenantId());
        assertEquals(CODE, captor.getValue().getDefinitionCode());
        assertEquals(1, captor.getValue().getDefinitionVersion().intValue());
        verify(formDataEngine).insert(eq(TABLE), any(FormData.class), anyList(), anyMap());
        assertEquals("张三", vo.getFields().get("name"));
    }

    @Test
    @DisplayName("submit：当前版本未发布抛参数错误且不落库")
    void shouldThrowWhenNotPublished() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistry>>any()))
                .thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, "{\"name\":\"张三\"}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDataMapper, never()).insert(any(FormData.class));
    }

    @Test
    @DisplayName("submit：定义不存在抛 NOT_FOUND")
    void shouldThrowWhenDefinitionMissing() {
        when(formDefinitionMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(99L, "{}"), "lisi", TENANT));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(formDataMapper, never()).insert(any(FormData.class));
    }

    @Test
    @DisplayName("submit：数据超过 64KB 抛参数错误")
    void shouldThrowWhenDataTooLong() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        String longData = "a".repeat(65537);

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, longData), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("表单数据长度不能超过 65536 字符", ex.getMessage());
    }

    @Test
    @DisplayName("submit：未知字段抛参数错误")
    void shouldThrowWhenUnknownField() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistry>>any()))
                .thenReturn(registry());

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, "{\"name\":\"张三\",\"hack\":1}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("字段 hack 不在表单结构定义中", ex.getMessage());
    }

    @Test
    @DisplayName("submit：必填字段缺失抛参数错误")
    void shouldThrowWhenRequiredMissing() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistry>>any()))
                .thenReturn(registry());

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, "{\"name\":\"\"}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("字段 姓名 为必填", ex.getMessage());
    }

    @Test
    @DisplayName("getDetail：租户匹配时返回主记录 + 物理表字段")
    void shouldGetDetail() {
        FormData entity = new FormData();
        entity.setId(1L);
        entity.setTenantId(TENANT);
        entity.setDefinitionCode(CODE);
        entity.setDefinitionVersion(1);
        when(formDataMapper.selectById(1L)).thenReturn(entity);
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistry>>any()))
                .thenReturn(registry());
        when(formDataEngine.selectFields(eq(TABLE), eq(1L), eq(TENANT), anyList()))
                .thenReturn(java.util.Map.of("name", "张三"));

        FormDataVO vo = formDataService.getDetail(1L, TENANT);

        assertEquals(1L, vo.getId());
        assertEquals("张三", vo.getFields().get("name"));
    }

    @Test
    @DisplayName("getDetail：跨租户访问抛 NOT_FOUND")
    void shouldThrowWhenCrossTenant() {
        FormData entity = new FormData();
        entity.setId(1L);
        entity.setTenantId(2L);
        when(formDataMapper.selectById(1L)).thenReturn(entity);

        BizException ex = assertThrows(BizException.class, () -> formDataService.getDetail(1L, TENANT));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("delete：主表与物理表双逻辑删除")
    void shouldDeleteBoth() {
        FormData entity = new FormData();
        entity.setId(7L);
        entity.setTenantId(TENANT);
        entity.setDefinitionCode(CODE);
        entity.setDefinitionVersion(1);
        when(formDataMapper.selectById(7L)).thenReturn(entity);
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistry>>any()))
                .thenReturn(registry());

        formDataService.delete(7L, TENANT);

        verify(formDataMapper).deleteById(7L);
        verify(formDataEngine).logicDelete(TABLE, 7L, TENANT);
    }

    @Test
    @DisplayName("delete：不存在的记录抛 NOT_FOUND 且不删除")
    void shouldThrowWhenDeleteMissing() {
        when(formDataMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> formDataService.delete(99L, TENANT));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(formDataMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("page：按租户与条件分页查询")
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
        request.setDefinitionCode(CODE);
        request.setSubmitter("lisi");

        PageResult<FormDataVO> result = formDataService.page(request, TENANT);

        assertEquals(1L, result.getTotal());
        assertEquals("lisi", result.getRecords().get(0).getSubmitter());
    }
}
