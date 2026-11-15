package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.model.request.FormDefinitionCopyRequest;
import com.bluewhale.form.model.request.FormDefinitionSaveRequest;
import com.bluewhale.form.model.dto.FormFieldConfigDTO;
import com.bluewhale.form.model.request.FormQueryRequest;
import com.bluewhale.form.po.FormColumnMetaPo;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormDefinitionVersionPo;
import com.bluewhale.form.po.FormFieldConfigPo;
import com.bluewhale.form.po.FormFieldOptionPo;
import com.bluewhale.form.po.FormTableDefinitionPo;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormDefinitionVersionMapper;
import com.bluewhale.form.mapper.FormFieldConfigMapper;
import com.bluewhale.form.mapper.FormFieldOptionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.model.vo.FormDefinitionVO;
import com.bluewhale.form.model.vo.FormDefinitionVersionVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormDefinitionServiceImpl} 表单定义增删改查与分页条件传递单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormDefinitionServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @Mock
    private FormFieldConfigMapper formFieldConfigMapper;

    @Mock
    private FormFieldOptionMapper formFieldOptionMapper;

    @Mock
    private FormColumnMetaMapper formColumnMetaMapper;

    @Mock
    private FormTableDefinitionMapper formTableDefinitionMapper;

    @Mock
    private FormDefinitionVersionMapper formDefinitionVersionMapper;

    @InjectMocks
    private FormDefinitionServiceImpl formDefinitionService;

    /**
     * 注册实体元信息缓存：Lambda 条件构造器会立即按实体解析列名，纯单测无 MyBatis 环境需手动初始化 TableInfo。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FormDefinitionPo.class);
        TableInfoHelper.initTableInfo(assistant, FormFieldConfigPo.class);
        TableInfoHelper.initTableInfo(assistant, FormFieldOptionPo.class);
        TableInfoHelper.initTableInfo(assistant, FormColumnMetaPo.class);
        TableInfoHelper.initTableInfo(assistant, FormTableDefinitionPo.class);
        TableInfoHelper.initTableInfo(assistant, FormDefinitionVersionPo.class);
    }

    /**
     * 构造表单定义保存请求桩对象。
     *
     * @param code 表单编码
     * @param name 表单名称
     * @return 保存请求
     */
    private static FormDefinitionSaveRequest saveRequest(String code, String name) {
        FormDefinitionSaveRequest request = new FormDefinitionSaveRequest();
        request.setCode(code);
        request.setName(name);
        return request;
    }

    @Test
    @DisplayName("create：编码唯一时落库并返回视图对象（默认版本 / 状态）")
    void shouldCreateWhenCodeUnique() {
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(0L);

        FormDefinitionVO vo = formDefinitionService.create(saveRequest("form-a", "A表单"));

        ArgumentCaptor<FormDefinitionPo> captor = ArgumentCaptor.forClass(FormDefinitionPo.class);
        verify(formDefinitionMapper).insert(captor.capture());
        assertEquals("form-a", captor.getValue().getCode());
        assertEquals("A表单", captor.getValue().getName());
        assertEquals(1, captor.getValue().getVersion().intValue());
        assertEquals(1, captor.getValue().getStatus().intValue());
        assertEquals("form-a", vo.getCode());
    }

    @Test
    @DisplayName("create：fields 与 schemaJson 同时提交抛参数错误且不落库")
    void shouldRejectFieldsAndSchemaJsonTogether() {
        FormDefinitionSaveRequest request = saveRequest("form_a", "A表单");
        FormFieldConfigDTO field = new FormFieldConfigDTO();
        field.setFieldLabel("名称");
        request.setFields(List.of(field));
        request.setSchemaJson("{}");

        BizException ex = assertThrows(BizException.class, () -> formDefinitionService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDefinitionMapper, never()).insert(any(FormDefinitionPo.class));
    }

    @Test
    @DisplayName("create：编码已存在时抛参数错误且不落库")
    void shouldRejectDuplicateCode() {
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> formDefinitionService.create(saveRequest("form-a", "A表单")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDefinitionMapper, never()).insert(any(FormDefinitionPo.class));
    }

    @Test
    @DisplayName("create：分类去首尾空白、标签按 JSON 字符串数组文本落库")
    void shouldPersistCategoryAndTagsOnCreate() {
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(0L);
        FormDefinitionSaveRequest request = saveRequest("form-a", "A表单");
        request.setCategory("  财务  ");
        request.setTags("[\"报销\",\"财务\"]");

        formDefinitionService.create(request);

        ArgumentCaptor<FormDefinitionPo> captor = ArgumentCaptor.forClass(FormDefinitionPo.class);
        verify(formDefinitionMapper).insert(captor.capture());
        assertEquals("财务", captor.getValue().getCategory());
        assertEquals("[\"报销\",\"财务\"]", captor.getValue().getTags());
    }

    @Test
    @DisplayName("page：分类非空时下推 category 等值条件（去首尾空白）")
    void shouldFilterPageByCategory() {
        Page<FormDefinitionPo> page = new Page<>(1L, 10L);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(formDefinitionMapper.selectPage(ArgumentMatchers.<Page<FormDefinitionPo>>any(),
                ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(page);
        FormQueryRequest request = new FormQueryRequest();
        request.setCategory("  财务 ");

        formDefinitionService.page(request);

        verify(formDefinitionMapper).selectPage(ArgumentMatchers.<Page<FormDefinitionPo>>any(),
                ArgumentMatchers.argThat(wrapper -> {
                    LambdaQueryWrapper<FormDefinitionPo> query = (LambdaQueryWrapper<FormDefinitionPo>) wrapper;
                    return query.getSqlSegment().contains("category")
                            && query.getParamNameValuePairs().containsValue("财务");
                }));
    }

    @Test
    @DisplayName("create：schema_json 超过 64KB 抛参数错误且不落库")
    void shouldRejectOversizeSchema() {
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(0L);
        FormDefinitionSaveRequest request = saveRequest("form-a", "A表单");
        request.setSchemaJson("x".repeat(65537));

        BizException ex = assertThrows(BizException.class, () -> formDefinitionService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDefinitionMapper, never()).insert(any(FormDefinitionPo.class));
    }

    @Test
    @DisplayName("update：存在时更新字段并返回视图对象")
    void shouldUpdateWhenExists() {
        FormDefinitionPo entity = new FormDefinitionPo();
        entity.setId(1L);
        entity.setCode("form-a");
        when(formDefinitionMapper.selectById(1L)).thenReturn(entity);
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfigPo>>any()))
                .thenReturn(List.of());
        when(formDefinitionMapper.update(any(FormDefinitionPo.class),
                ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(1);
        FormDefinitionSaveRequest request = saveRequest("form-a", "A表单新版");
        request.setSchemaJson("{\"fields\":[]}");
        request.setRemark("备注");

        FormDefinitionVO vo = formDefinitionService.update(1L, request);

        verify(formDefinitionMapper).update(any(FormDefinitionPo.class),
                ArgumentMatchers.<Wrapper<FormDefinitionPo>>any());
        assertEquals("A表单新版", entity.getName());
        assertEquals("{\"fields\":[]}", entity.getSchemaJson());
        assertEquals("备注", vo.getRemark());
    }

    @Test
    @DisplayName("update：版本号已被并发修改时抛参数错误")
    void shouldRejectConcurrentUpdate() {
        FormDefinitionPo entity = new FormDefinitionPo();
        entity.setId(1L);
        entity.setCode("form-a");
        entity.setVersion(2);
        when(formDefinitionMapper.selectById(1L)).thenReturn(entity);
        when(formDefinitionMapper.update(any(FormDefinitionPo.class),
                ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> formDefinitionService.update(1L, saveRequest("form-a", "A表单新版")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("update：ID 不存在抛 NOT_FOUND 且不更新")
    void shouldThrowWhenUpdateMissing() {
        when(formDefinitionMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> formDefinitionService.update(99L, saveRequest("form-a", "A表单")));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(formDefinitionMapper, never()).updateById(any(FormDefinitionPo.class));
    }

    @Test
    @DisplayName("delete：ID 不存在抛 NOT_FOUND 且不删除")
    void shouldThrowWhenDeleteMissing() {
        when(formDefinitionMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> formDefinitionService.delete(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(formDefinitionMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    @DisplayName("getDetail：存在时返回视图对象")
    void shouldGetDetailWhenExists() {
        FormDefinitionPo entity = new FormDefinitionPo();
        entity.setId(1L);
        entity.setCode("form-a");
        entity.setName("A表单");
        when(formDefinitionMapper.selectById(1L)).thenReturn(entity);
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfigPo>>any()))
                .thenReturn(List.of());

        FormDefinitionVO vo = formDefinitionService.getDetail(1L);

        assertEquals(1L, vo.getId());
        assertEquals("form-a", vo.getCode());
        assertEquals("A表单", vo.getName());
    }

    @Test
    @DisplayName("page：按条件调用分页查询并映射结果")
    void shouldPageWithConditions() {
        FormDefinitionPo entity = new FormDefinitionPo();
        entity.setId(1L);
        entity.setCode("form-a");
        entity.setName("A表单");
        Page<FormDefinitionPo> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(formDefinitionMapper.selectPage(ArgumentMatchers.<Page<FormDefinitionPo>>any(),
                ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(page);
        FormQueryRequest request = new FormQueryRequest();
        request.setName("A");
        request.setCode("form-a");
        request.setStatus(1);

        PageResult<FormDefinitionVO> result = formDefinitionService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("form-a", result.getRecords().get(0).getCode());
        verify(formDefinitionMapper).selectPage(ArgumentMatchers.<Page<FormDefinitionPo>>any(),
                ArgumentMatchers.<Wrapper<FormDefinitionPo>>any());
    }

    @Test
    @DisplayName("create：元数据模型按 dto 持久化 colSpan（缺省 24）")
    void shouldPersistColSpanOnCreate() {
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(0L);
        FormTableDefinitionPo table = new FormTableDefinitionPo();
        table.setId(9L);
        when(formTableDefinitionMapper.selectById(9L)).thenReturn(table);
        FormColumnMetaPo column = new FormColumnMetaPo();
        column.setId(7L);
        column.setTableDefinitionId(9L);
        column.setColumnCode("guest_name");
        column.setColumnName("姓名");
        column.setDataType("text");
        column.setRequired(0);
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column));
        when(formFieldOptionMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldOptionPo>>any()))
                .thenReturn(List.of());
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfigPo>>any()))
                .thenReturn(List.of());
        FormDefinitionSaveRequest request = saveRequest("form-a", "A表单");
        request.setTableDefinitionId(9L);
        FormFieldConfigDTO dataField = new FormFieldConfigDTO();
        dataField.setColumnMetaId(7L);
        dataField.setFieldLabel("姓名");
        dataField.setColSpan(12);
        FormFieldConfigDTO layoutField = new FormFieldConfigDTO();
        layoutField.setFieldLabel("标题");
        layoutField.setWidgetType("title");
        request.setFields(List.of(dataField, layoutField));

        formDefinitionService.create(request);

        ArgumentCaptor<FormFieldConfigPo> captor = ArgumentCaptor.forClass(FormFieldConfigPo.class);
        verify(formFieldConfigMapper, times(2)).insert(captor.capture());
        assertEquals(12, captor.getAllValues().get(0).getColSpan().intValue());
        assertEquals(24, captor.getAllValues().get(1).getColSpan().intValue());
    }

    @Test
    @DisplayName("getDetail：元数据模型回填关联列信息（columnCode/dataType/required/maxLength/colSpan）")
    void shouldEnrichColumnInfoOnDetail() {
        FormDefinitionPo entity = new FormDefinitionPo();
        entity.setId(1L);
        entity.setCode("form-a");
        entity.setTableDefinitionId(9L);
        when(formDefinitionMapper.selectById(1L)).thenReturn(entity);
        FormFieldConfigPo config = new FormFieldConfigPo();
        config.setId(100L);
        config.setFormId(1L);
        config.setColumnMetaId(7L);
        config.setFieldLabel("姓名");
        config.setColSpan(12);
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfigPo>>any()))
                .thenReturn(List.of(config));
        when(formFieldOptionMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldOptionPo>>any()))
                .thenReturn(List.of());
        FormColumnMetaPo column = new FormColumnMetaPo();
        column.setId(7L);
        column.setTableDefinitionId(9L);
        column.setColumnCode("guest_name");
        column.setDataType("text");
        column.setRequired(1);
        column.setLength(64);
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column));

        FormDefinitionVO vo = formDefinitionService.getDetail(1L);

        assertEquals(1, vo.getFields().size());
        assertEquals("guest_name", vo.getFields().get(0).getColumnCode());
        assertEquals("text", vo.getFields().get(0).getDataType());
        assertEquals(1, vo.getFields().get(0).getRequired().intValue());
        assertEquals(64, vo.getFields().get(0).getMaxLength().intValue());
        assertEquals(12, vo.getFields().get(0).getColSpan().intValue());
        verify(formColumnMetaMapper).selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any());
    }

    /**
     * 构造源表单定义桩对象（旧模型）。
     *
     * @param id 定义ID
     * @param code 编码
     * @param name 名称
     * @return 表单定义
     */
    private static FormDefinitionPo sourceDefinition(Long id, String code, String name) {
        FormDefinitionPo source = new FormDefinitionPo();
        source.setId(id);
        source.setCode(code);
        source.setName(name);
        source.setSchemaJson("{\"fields\":[]}");
        source.setVersion(3);
        source.setStatus(1);
        return source;
    }

    @Test
    @DisplayName("copy：未指定编码/名称时生成 _copy 与「（副本）」且版本为 1")
    void shouldCopyWithGeneratedCodeAndName() {
        when(formDefinitionMapper.selectById(1L)).thenReturn(sourceDefinition(1L, "form-a", "A表单"));
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(0L);
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfigPo>>any()))
                .thenReturn(List.of());

        FormDefinitionVO vo = formDefinitionService.copy(1L, null);

        ArgumentCaptor<FormDefinitionPo> captor = ArgumentCaptor.forClass(FormDefinitionPo.class);
        verify(formDefinitionMapper).insert(captor.capture());
        assertEquals("form-a_copy", captor.getValue().getCode());
        assertEquals("A表单（副本）", captor.getValue().getName());
        assertEquals(1, captor.getValue().getVersion().intValue());
        assertEquals(1, captor.getValue().getStatus().intValue());
        assertEquals("{\"fields\":[]}", captor.getValue().getSchemaJson());
        assertEquals("form-a_copy", vo.getCode());
    }

    @Test
    @DisplayName("copy：显式编码已存在时抛参数错误且不落库")
    void shouldRejectCopyWhenExplicitCodeDuplicate() {
        when(formDefinitionMapper.selectById(1L)).thenReturn(sourceDefinition(1L, "form-a", "A表单"));
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(1L);
        FormDefinitionCopyRequest request = new FormDefinitionCopyRequest();
        request.setCode("form-b");

        BizException ex = assertThrows(BizException.class,
                () -> formDefinitionService.copy(1L, request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDefinitionMapper, never()).insert(any(FormDefinitionPo.class));
    }

    @Test
    @DisplayName("copy：显式编码/名称生效，字段与选项被复制")
    void shouldCopyFieldsAndOptions() {
        when(formDefinitionMapper.selectById(1L)).thenReturn(sourceDefinition(1L, "form-a", "A表单"));
        FormFieldConfigPo first = new FormFieldConfigPo();
        first.setId(11L);
        first.setFormId(1L);
        first.setFieldLabel("姓名");
        first.setSortNo(0);
        FormFieldConfigPo second = new FormFieldConfigPo();
        second.setId(12L);
        second.setFormId(1L);
        second.setFieldLabel("状态");
        second.setSortNo(1);
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfigPo>>any()))
                .thenReturn(List.of(first, second));
        FormFieldOptionPo option = new FormFieldOptionPo();
        option.setId(21L);
        option.setFieldConfigId(12L);
        option.setOptionLabel("启用");
        option.setOptionValue("A");
        option.setSortNo(0);
        when(formFieldOptionMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldOptionPo>>any()))
                .thenReturn(List.of(option));
        FormDefinitionCopyRequest request = new FormDefinitionCopyRequest();
        request.setCode("form-b");
        request.setName("B表单");

        FormDefinitionVO vo = formDefinitionService.copy(1L, request);

        assertEquals("form-b", vo.getCode());
        assertEquals("B表单", vo.getName());
        ArgumentCaptor<FormFieldConfigPo> configCaptor = ArgumentCaptor.forClass(FormFieldConfigPo.class);
        verify(formFieldConfigMapper, times(2)).insert(configCaptor.capture());
        assertEquals("姓名", configCaptor.getAllValues().get(0).getFieldLabel());
        assertNull(configCaptor.getAllValues().get(0).getId());
        ArgumentCaptor<FormFieldOptionPo> optionCaptor = ArgumentCaptor.forClass(FormFieldOptionPo.class);
        verify(formFieldOptionMapper).insert(optionCaptor.capture());
        assertEquals("A", optionCaptor.getValue().getOptionValue());
    }

    @Test
    @DisplayName("create：成功落库后写入版本快照")
    void shouldWriteSnapshotOnCreate() {
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(0L);

        formDefinitionService.create(saveRequest("form-a", "A表单"));

        ArgumentCaptor<FormDefinitionVersionPo> captor = ArgumentCaptor.forClass(FormDefinitionVersionPo.class);
        verify(formDefinitionVersionMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getVersion().intValue());
        assertTrue(captor.getValue().getSnapshotJson().contains("\"code\":\"form-a\""));
        assertTrue(captor.getValue().getSnapshotJson().contains("\"fields\":[]"));
        assertEquals(1L, captor.getValue().getSnapshotSeq().longValue());
    }

    @Test
    @DisplayName("update：成功后按当前版本号写入快照（版本号保持不变）")
    void shouldWriteSnapshotOnUpdate() {
        FormDefinitionPo entity = sourceDefinition(1L, "form-a", "A表单");
        entity.setVersion(2);
        when(formDefinitionMapper.selectById(1L)).thenReturn(entity);
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfigPo>>any()))
                .thenReturn(List.of());
        when(formDefinitionMapper.update(any(FormDefinitionPo.class),
                ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(1);

        formDefinitionService.update(1L, saveRequest("form-a", "A表单新版"));

        ArgumentCaptor<FormDefinitionVersionPo> captor = ArgumentCaptor.forClass(FormDefinitionVersionPo.class);
        verify(formDefinitionVersionMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getVersion().intValue());
        assertEquals(2, entity.getVersion().intValue());
    }

    @Test
    @DisplayName("update：版本号回退抛参数错误且不落库")
    void shouldRejectVersionDowngrade() {
        FormDefinitionPo entity = sourceDefinition(1L, "form-a", "A表单");
        entity.setVersion(3);
        when(formDefinitionMapper.selectById(1L)).thenReturn(entity);
        FormDefinitionSaveRequest request = saveRequest("form-a", "A表单");
        request.setVersion(2);

        BizException ex = assertThrows(BizException.class,
                () -> formDefinitionService.update(1L, request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDefinitionMapper, never()).update(any(FormDefinitionPo.class),
                ArgumentMatchers.<Wrapper<FormDefinitionPo>>any());
        verify(formDefinitionVersionMapper, never()).insert(any(FormDefinitionVersionPo.class));
    }

    @Test
    @DisplayName("rollback：恢复快照的定义与字段，版本号加一")
    void shouldRollbackAndIncreaseVersion() {
        FormDefinitionPo entity = sourceDefinition(1L, "form-a", "当前版本");
        entity.setVersion(3);
        when(formDefinitionMapper.selectById(1L)).thenReturn(entity);
        FormDefinitionVersionPo snapshot = new FormDefinitionVersionPo();
        snapshot.setId(9L);
        snapshot.setDefinitionId(1L);
        snapshot.setVersion(2);
        snapshot.setSnapshotJson("{\"definition\":{\"code\":\"form-a\",\"name\":\"旧版\","
                + "\"tableDefinitionId\":null,\"schemaJson\":\"{\\\"fields\\\":[]}\",\"version\":2,"
                + "\"status\":1,\"remark\":\"r\"},\"fields\":[{\"columnMetaId\":null,"
                + "\"fieldLabel\":\"标题\",\"widgetType\":\"title\",\"showInList\":1,\"showInForm\":1,"
                + "\"editable\":1,\"sortNo\":0,\"colSpan\":24,\"placeholder\":null,\"defaultValue\":null,"
                + "\"validateJson\":null,\"remark\":null,\"status\":1,\"options\":[{\"optionLabel\":\"A\","
                + "\"optionValue\":\"A\",\"sortNo\":0}]}]}");
        when(formDefinitionVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<FormDefinitionVersionPo>>any())).thenReturn(List.of(snapshot));
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfigPo>>any()))
                .thenReturn(List.of());
        when(formDefinitionMapper.update(any(FormDefinitionPo.class),
                ArgumentMatchers.<Wrapper<FormDefinitionPo>>any())).thenReturn(1);

        FormDefinitionVO vo = formDefinitionService.rollback(1L, 2);

        assertEquals("旧版", entity.getName());
        assertEquals("{\"fields\":[]}", entity.getSchemaJson());
        assertEquals(4, entity.getVersion().intValue());
        assertEquals(4, vo.getVersion().intValue());
        verify(formDefinitionMapper).update(any(FormDefinitionPo.class),
                ArgumentMatchers.<Wrapper<FormDefinitionPo>>any());
        ArgumentCaptor<FormFieldConfigPo> configCaptor = ArgumentCaptor.forClass(FormFieldConfigPo.class);
        verify(formFieldConfigMapper).insert(configCaptor.capture());
        assertEquals("标题", configCaptor.getValue().getFieldLabel());
        verify(formFieldOptionMapper).insert(any(FormFieldOptionPo.class));
        ArgumentCaptor<FormDefinitionVersionPo> versionCaptor =
                ArgumentCaptor.forClass(FormDefinitionVersionPo.class);
        verify(formDefinitionVersionMapper).insert(versionCaptor.capture());
        assertEquals(4, versionCaptor.getValue().getVersion().intValue());
    }

    @Test
    @DisplayName("rollback：目标版本快照不存在抛 NOT_FOUND 且不更新")
    void shouldThrowWhenRollbackVersionMissing() {
        when(formDefinitionMapper.selectById(1L)).thenReturn(sourceDefinition(1L, "form-a", "A表单"));
        when(formDefinitionVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<FormDefinitionVersionPo>>any())).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class,
                () -> formDefinitionService.rollback(1L, 99));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(formDefinitionMapper, never()).updateById(any(FormDefinitionPo.class));
    }

    @Test
    @DisplayName("listVersions：按版本号降序返回版本与创建时间")
    void shouldListVersionsDescending() {
        when(formDefinitionMapper.selectById(1L)).thenReturn(sourceDefinition(1L, "form-a", "A表单"));
        FormDefinitionVersionPo latest = new FormDefinitionVersionPo();
        latest.setVersion(3);
        latest.setCreateTime(LocalDateTime.of(2026, 10, 23, 12, 0));
        FormDefinitionVersionPo older = new FormDefinitionVersionPo();
        older.setVersion(2);
        older.setCreateTime(LocalDateTime.of(2026, 10, 22, 12, 0));
        when(formDefinitionVersionMapper.selectList(
                ArgumentMatchers.<Wrapper<FormDefinitionVersionPo>>any()))
                .thenReturn(List.of(latest, older));

        List<FormDefinitionVersionVO> versions = formDefinitionService.listVersions(1L);

        assertEquals(2, versions.size());
        assertEquals(3, versions.get(0).getVersion().intValue());
        assertEquals(2, versions.get(1).getVersion().intValue());
        assertEquals(LocalDateTime.of(2026, 10, 23, 12, 0), versions.get(0).getCreateTime());
    }
}
