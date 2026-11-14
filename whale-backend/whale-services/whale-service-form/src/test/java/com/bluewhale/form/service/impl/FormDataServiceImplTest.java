package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.model.request.FormDataQueryRequest;
import com.bluewhale.form.model.request.FormDataSubmitRequest;
import com.bluewhale.form.model.request.FormDataUpdateRequest;
import com.bluewhale.form.po.FormDataPo;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormField;
import com.bluewhale.form.model.vo.FormDataVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
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
    private FormColumnMetaMapper formColumnMetaMapper;

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
    private static FormDefinitionPo definition(Long id) {
        FormDefinitionPo definition = new FormDefinitionPo();
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
    private static FormTableRegistryPo registry() {
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setDefinitionId(10L);
        registry.setDefinitionCode(CODE);
        registry.setDefinitionVersion(1);
        registry.setPhysicalTable(TABLE);
        registry.setSchemaJson("{\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\",\"required\":true}]}");
        return registry;
    }

    /**
     * 构造使用指定 schema 的物理表登记桩对象。
     *
     * @param schemaJson 表单结构定义
     * @return 登记实体
     */
    private static FormTableRegistryPo registryWithSchema(String schemaJson) {
        FormTableRegistryPo registry = registry();
        registry.setSchemaJson(schemaJson);
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

    /**
     * 构造携带幂等键的提交请求桩对象。
     *
     * @param definitionId 表单定义ID
     * @param dataJson 表单数据JSON
     * @param clientRequestId 客户端幂等键
     * @return 提交请求
     */
    private static FormDataSubmitRequest submitRequest(Long definitionId, String dataJson, String clientRequestId) {
        FormDataSubmitRequest request = submitRequest(definitionId, dataJson);
        request.setClientRequestId(clientRequestId);
        return request;
    }

    /**
     * 构造已落库的表单数据桩对象。
     *
     * @param id 主键ID
     * @param dataJson 表单数据JSON
     * @return 表单数据实体
     */
    private static FormDataPo existingData(Long id, String dataJson) {
        FormDataPo entity = new FormDataPo();
        entity.setId(id);
        entity.setTenantId(TENANT);
        entity.setDefinitionCode(CODE);
        entity.setDefinitionVersion(1);
        entity.setDataJson(dataJson);
        return entity;
    }

    @Test
    @DisplayName("submit：顶层基础字段类型不符返回参数错误且不落库")
    void shouldRejectTopLevelTypeMismatch() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema(
                        "{\"fields\":[{\"key\":\"age\",\"label\":\"年龄\",\"type\":\"number\",\"required\":true}]}"));

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, "{\"age\":\"abc\"}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDataMapper, never()).insert(any(FormDataPo.class));
        verify(formDataEngine, never()).insert(anyString(), any(FormDataPo.class), anyList(), anyMap());
    }

    @Test
    @DisplayName("submit：日期字段格式非法返回参数错误")
    void shouldRejectInvalidDateValue() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema(
                        "{\"fields\":[{\"key\":\"birthday\",\"label\":\"生日\",\"type\":\"date\",\"required\":true}]}"));

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, "{\"birthday\":\"2026-13-01\"}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDataMapper, never()).insert(any(FormDataPo.class));
    }

    @Test
    @DisplayName("submit：定义存在且版本已发布时双写主表与物理表")
    void shouldSubmitWhenPublished() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());

        FormDataVO vo = formDataService.submit(submitRequest(10L, "{\"name\":\"张三\"}"), "lisi", TENANT);

        ArgumentCaptor<FormDataPo> captor = ArgumentCaptor.forClass(FormDataPo.class);
        verify(formDataMapper).insert(captor.capture());
        assertEquals(TENANT, captor.getValue().getTenantId());
        assertEquals(CODE, captor.getValue().getDefinitionCode());
        assertEquals(1, captor.getValue().getDefinitionVersion().intValue());
        verify(formDataEngine).insert(eq(TABLE), any(FormDataPo.class), anyList(), anyMap());
        assertEquals("张三", vo.getFields().get("name"));
    }

    @Test
    @DisplayName("submit：幂等键已存在时返回首次提交结果，不重复落库")
    void shouldReturnExistingWhenClientRequestIdHit() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formDataMapper.selectOne(ArgumentMatchers.<Wrapper<FormDataPo>>any()))
                .thenReturn(existingData(7L, "{\"name\":\"张三\"}"));

        FormDataVO vo = formDataService.submit(submitRequest(10L, "{\"name\":\"张三\"}", "req-1"), "lisi", TENANT);

        assertEquals(7L, vo.getId().longValue());
        assertEquals("张三", vo.getFields().get("name"));
        verify(formDataMapper, never()).insert(any(FormDataPo.class));
        verify(formDataEngine, never()).insert(anyString(), any(FormDataPo.class), anyList(), anyMap());
    }

    @Test
    @DisplayName("submit：并发唯一键冲突时回查并返回首次提交结果")
    void shouldReturnExistingWhenDuplicateKey() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        when(formDataMapper.selectOne(ArgumentMatchers.<Wrapper<FormDataPo>>any()))
                .thenReturn(null, existingData(7L, "{\"name\":\"张三\"}"));
        when(formDataMapper.insert(any(FormDataPo.class))).thenThrow(new DuplicateKeyException("uk"));

        FormDataVO vo = formDataService.submit(submitRequest(10L, "{\"name\":\"张三\"}", "req-1"), "lisi", TENANT);

        assertEquals(7L, vo.getId().longValue());
        verify(formDataEngine, never()).insert(anyString(), any(FormDataPo.class), anyList(), anyMap());
    }

    @Test
    @DisplayName("submit：幂等键被软删记录占用时返回明确参数错误")
    void shouldThrowWhenClientRequestIdOccupiedByDeletedRecord() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        when(formDataMapper.selectOne(ArgumentMatchers.<Wrapper<FormDataPo>>any())).thenReturn(null);
        when(formDataMapper.insert(any(FormDataPo.class))).thenThrow(new DuplicateKeyException("uk"));

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, "{\"name\":\"张三\"}", "req-1"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("客户端请求ID已被占用，请重新生成 clientRequestId", ex.getMessage());
        verify(formDataEngine, never()).insert(anyString(), any(FormDataPo.class), anyList(), anyMap());
    }

    @Test
    @DisplayName("submit：当前版本未发布抛参数错误且不落库")
    void shouldThrowWhenNotPublished() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, "{\"name\":\"张三\"}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDataMapper, never()).insert(any(FormDataPo.class));
    }

    @Test
    @DisplayName("submit：定义不存在抛 NOT_FOUND")
    void shouldThrowWhenDefinitionMissing() {
        when(formDefinitionMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(99L, "{}"), "lisi", TENANT));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(formDataMapper, never()).insert(any(FormDataPo.class));
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
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
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
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, "{\"name\":\"\"}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("字段 姓名 为必填", ex.getMessage());
    }

    @Test
    @DisplayName("getDetail：租户匹配时返回主记录 + 物理表字段")
    void shouldGetDetail() {
        FormDataPo entity = new FormDataPo();
        entity.setId(1L);
        entity.setTenantId(TENANT);
        entity.setDefinitionCode(CODE);
        entity.setDefinitionVersion(1);
        when(formDataMapper.selectById(1L)).thenReturn(entity);
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        when(formDataEngine.selectFields(eq(TABLE), eq(1L), eq(TENANT), anyList()))
                .thenReturn(java.util.Map.of("name", "张三"));

        FormDataVO vo = formDataService.getDetail(1L, TENANT);

        assertEquals(1L, vo.getId());
        assertEquals("张三", vo.getFields().get("name"));
    }

    @Test
    @DisplayName("getDetail：表级快照含无 children 的 subtable 时仍可回读")
    void shouldGetDetailWithStorageSnapshotSubtable() {
        FormDataPo entity = existingData(1L, "{}");
        entity.setDefinitionId(10L);
        when(formDataMapper.selectById(1L)).thenReturn(entity);
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema(
                        "{\"fields\":[{\"key\":\"lines\",\"label\":\"明细\",\"type\":\"subtable\"}]}"));
        when(formDataEngine.selectFields(eq(TABLE), eq(1L), eq(TENANT), anyList()))
                .thenReturn(java.util.Map.of());

        FormDataVO vo = formDataService.getDetail(1L, TENANT);

        assertEquals(1L, vo.getId());
    }

    @Test
    @DisplayName("update：必填校验与物理写入只使用当前视图字段，不触碰表级其他列")
    @SuppressWarnings("unchecked")
    void shouldUpdateWithViewFieldsOnly() {
        FormDataPo entity = existingData(1L, "{\"remark\":\"旧\"}");
        entity.setDefinitionId(10L);
        when(formDataMapper.selectById(1L)).thenReturn(entity);
        FormDefinitionPo definition = definition(10L);
        definition.setSchemaJson("{\"fields\":[{\"key\":\"remark\",\"label\":\"备注\",\"type\":\"text\"}]}");
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition);
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema("{\"fields\":["
                        + "{\"key\":\"amount\",\"label\":\"金额\",\"type\":\"number\",\"required\":true},"
                        + "{\"key\":\"remark\",\"label\":\"备注\",\"type\":\"text\"}]}"));
        FormDataUpdateRequest request = new FormDataUpdateRequest();
        request.setDataJson("{\"remark\":\"新\"}");

        formDataService.update(1L, request, TENANT);

        ArgumentCaptor<List<FormField>> captor = ArgumentCaptor.forClass(List.class);
        verify(formDataEngine).update(eq(TABLE), any(FormDataPo.class), captor.capture(), anyMap());
        assertEquals(1, captor.getValue().size());
        assertEquals("remark", captor.getValue().get(0).getKey());
    }

    @Test
    @DisplayName("getDetail：跨租户访问抛 NOT_FOUND")
    void shouldThrowWhenCrossTenant() {
        FormDataPo entity = new FormDataPo();
        entity.setId(1L);
        entity.setTenantId(2L);
        when(formDataMapper.selectById(1L)).thenReturn(entity);

        BizException ex = assertThrows(BizException.class, () -> formDataService.getDetail(1L, TENANT));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("delete：主表与物理表双逻辑删除")
    void shouldDeleteBoth() {
        FormDataPo entity = new FormDataPo();
        entity.setId(7L);
        entity.setTenantId(TENANT);
        entity.setDefinitionCode(CODE);
        entity.setDefinitionVersion(1);
        when(formDataMapper.selectById(7L)).thenReturn(entity);
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
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
        FormDataPo entity = new FormDataPo();
        entity.setId(1L);
        entity.setSubmitter("lisi");
        Page<FormDataPo> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(formDataMapper.selectPage(ArgumentMatchers.<Page<FormDataPo>>any(),
                ArgumentMatchers.<Wrapper<FormDataPo>>any())).thenReturn(page);
        FormDataQueryRequest request = new FormDataQueryRequest();
        request.setDefinitionCode(CODE);
        request.setSubmitter("lisi");

        PageResult<FormDataVO> result = formDataService.page(request, TENANT);

        assertEquals(1L, result.getTotal());
        assertEquals("lisi", result.getRecords().get(0).getSubmitter());
    }

    @Test
    @DisplayName("submit：attachment 值为数字数组时通过")
    void shouldSubmitAttachmentArray() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema("{\"fields\":[{\"key\":\"files\",\"label\":\"附件\","
                        + "\"type\":\"attachment\",\"multiple\":true,\"maxCount\":3}]}"));

        FormDataVO vo = formDataService.submit(
                submitRequest(10L, "{\"files\":[2113434502638284801,2]}"), "lisi", TENANT);

        List<?> files = (List<?>) vo.getFields().get("files");
        assertEquals(2, files.size());
    }

    @Test
    @DisplayName("submit：attachment 值非数组抛参数错误")
    void shouldThrowWhenAttachmentNotArray() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema("{\"fields\":[{\"key\":\"files\",\"label\":\"附件\","
                        + "\"type\":\"attachment\"}]}"));

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, "{\"files\":\"211\"}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDataMapper, never()).insert(any(FormDataPo.class));
    }

    @Test
    @DisplayName("submit：attachment 数组元素为字符串抛参数错误")
    void shouldThrowWhenAttachmentElementNotNumber() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema("{\"fields\":[{\"key\":\"files\",\"label\":\"附件\","
                        + "\"type\":\"attachment\"}]}"));

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, "{\"files\":[\"211\"]}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("submit：attachment 超过 maxCount 抛参数错误")
    void shouldThrowWhenAttachmentExceedsMaxCount() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema("{\"fields\":[{\"key\":\"files\",\"label\":\"附件\","
                        + "\"type\":\"attachment\",\"maxCount\":1}]}"));

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, "{\"files\":[1,2]}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    /**
     * 构造子表 schema 桩文本。
     *
     * @param maxRows 最大行数（0 不限制）
     * @return schema JSON
     */
    private static String subtableSchema(int maxRows) {
        return "{\"fields\":[{\"key\":\"lines\",\"label\":\"明细\",\"type\":\"subtable\",\"maxRows\":"
                + maxRows + ",\"children\":[{\"key\":\"product\",\"label\":\"商品\",\"type\":\"text\","
                + "\"required\":true},{\"key\":\"amount\",\"label\":\"数量\",\"type\":\"number\"}]}]}";
    }

    @Test
    @DisplayName("submit：subtable 合法行通过且忽略空行")
    void shouldSubmitSubtableAndIgnoreEmptyRows() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema(subtableSchema(1)));

        FormDataVO vo = formDataService.submit(submitRequest(10L,
                "{\"lines\":[{\"product\":\"\",\"amount\":null},{\"product\":\"苹果\",\"amount\":2}]}"),
                "lisi", TENANT);

        List<?> lines = (List<?>) vo.getFields().get("lines");
        assertEquals(2, lines.size());
    }

    @Test
    @DisplayName("submit：subtable 行必填子字段缺失抛参数错误")
    void shouldThrowWhenSubtableRequiredMissing() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema(subtableSchema(0)));

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L,
                        "{\"lines\":[{\"amount\":2}]}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("商品"));
    }

    @Test
    @DisplayName("submit：subtable 子字段类型不符抛参数错误")
    void shouldThrowWhenSubtableTypeMismatch() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema(subtableSchema(0)));

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L,
                        "{\"lines\":[{\"product\":\"苹果\",\"amount\":\"两个\"}]}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("submit：subtable 超过 maxRows 抛参数错误")
    void shouldThrowWhenSubtableExceedsMaxRows() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema(subtableSchema(1)));

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L,
                        "{\"lines\":[{\"product\":\"苹果\"},{\"product\":\"梨\"}]}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("不能超过"));
    }

    @Test
    @DisplayName("submit：subtable 行不是对象抛参数错误")
    void shouldThrowWhenSubtableRowNotObject() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registryWithSchema(subtableSchema(0)));

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.submit(submitRequest(10L, "{\"lines\":[\"x\"]}"), "lisi", TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    /**
     * 构造含动态过滤字段的表单结构定义。
     *
     * @return schema JSON
     */
    private static String filterSchema() {
        return "{\"fields\":[{\"key\":\"amount\",\"label\":\"金额\",\"type\":\"number\"},"
                + "{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\"}]}";
    }

    /**
     * 构造带创建时间的数据实体桩对象。
     *
     * @param id 主键
     * @param minute 创建时间分钟（用于排序断言）
     * @return 数据实体
     */
    private static FormDataPo data(Long id, int minute) {
        FormDataPo entity = new FormDataPo();
        entity.setId(id);
        entity.setTenantId(TENANT);
        entity.setDefinitionCode(CODE);
        entity.setCreateTime(java.time.LocalDateTime.of(2026, 10, 26, 10, minute));
        return entity;
    }

    @Test
    @DisplayName("page：动态字段条件检索按物理表命中回捞并按创建时间倒序")
    void shouldPageWithDynamicFilters() {
        FormDefinitionPo definition = definition(10L);
        definition.setSchemaJson(filterSchema());
        when(formDefinitionMapper.selectList(ArgumentMatchers.<Wrapper<FormDefinitionPo>>any()))
                .thenReturn(List.of(definition));
        when(formTableRegistryMapper.selectList(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(List.of(registryWithSchema(filterSchema())));
        when(formDataEngine.countByFilters(eq(TABLE), eq(TENANT), anyList(), anyList(),
                ArgumentMatchers.<String>any(), ArgumentMatchers.<String>any())).thenReturn(2L);
        when(formDataEngine.selectIdsByFilters(eq(TABLE), eq(TENANT), anyList(), anyList(),
                ArgumentMatchers.<String>any(), ArgumentMatchers.<String>any(), eq(10)))
                .thenReturn(List.of(2L, 1L));
        when(formDataMapper.selectBatchIds(any())).thenReturn(List.of(data(1L, 1), data(2L, 2)));
        FormDataQueryRequest request = new FormDataQueryRequest();
        request.setDefinitionCode(CODE);
        request.setFilters("[{\"field\":\"amount\",\"op\":\"gte\",\"value\":100}]");
        request.setCurrent(1);
        request.setSize(10);

        PageResult<FormDataVO> result = formDataService.page(request, TENANT);

        assertEquals(2L, result.getTotal());
        assertEquals(List.of(2L, 1L), result.getRecords().stream().map(FormDataVO::getId).toList());
    }

    @Test
    @DisplayName("page：过滤字段不在表单结构定义中抛参数错误")
    void shouldThrowWhenFilterFieldMissing() {
        FormDefinitionPo definition = definition(10L);
        definition.setSchemaJson(filterSchema());
        when(formDefinitionMapper.selectList(ArgumentMatchers.<Wrapper<FormDefinitionPo>>any()))
                .thenReturn(List.of(definition));
        FormDataQueryRequest request = new FormDataQueryRequest();
        request.setDefinitionCode(CODE);
        request.setFilters("[{\"field\":\"hack\",\"op\":\"eq\",\"value\":1}]");

        BizException ex = assertThrows(BizException.class, () -> formDataService.page(request, TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("过滤字段不在当前表单结构定义中"));
        verify(formDataEngine, never()).countByFilters(any(), any(), anyList(), anyList(), any(), any());
    }

    @Test
    @DisplayName("page：动态条件未指定表单编码抛参数错误")
    void shouldThrowWhenFiltersWithoutDefinitionCode() {
        FormDataQueryRequest request = new FormDataQueryRequest();
        request.setFilters("[{\"field\":\"amount\",\"op\":\"eq\",\"value\":1}]");

        BizException ex = assertThrows(BizException.class, () -> formDataService.page(request, TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("page：filters 非法 JSON 抛参数错误")
    void shouldThrowWhenFiltersInvalidJson() {
        FormDataQueryRequest request = new FormDataQueryRequest();
        request.setDefinitionCode(CODE);
        request.setFilters("not-json");

        BizException ex = assertThrows(BizException.class, () -> formDataService.page(request, TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }
}
