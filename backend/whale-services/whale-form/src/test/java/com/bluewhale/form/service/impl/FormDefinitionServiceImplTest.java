package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormDefinitionSaveRequest;
import com.bluewhale.form.dto.FormFieldConfigDTO;
import com.bluewhale.form.dto.FormQueryRequest;
import com.bluewhale.form.entity.FormColumnMeta;
import com.bluewhale.form.entity.FormDefinition;
import com.bluewhale.form.entity.FormFieldConfig;
import com.bluewhale.form.entity.FormFieldOption;
import com.bluewhale.form.entity.FormTableDefinition;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormFieldConfigMapper;
import com.bluewhale.form.mapper.FormFieldOptionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.vo.FormDefinitionVO;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @InjectMocks
    private FormDefinitionServiceImpl formDefinitionService;

    /**
     * 注册实体元信息缓存：Lambda 条件构造器会立即按实体解析列名，纯单测无 MyBatis 环境需手动初始化 TableInfo。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FormDefinition.class);
        TableInfoHelper.initTableInfo(assistant, FormFieldConfig.class);
        TableInfoHelper.initTableInfo(assistant, FormFieldOption.class);
        TableInfoHelper.initTableInfo(assistant, FormColumnMeta.class);
        TableInfoHelper.initTableInfo(assistant, FormTableDefinition.class);
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
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinition>>any())).thenReturn(0L);

        FormDefinitionVO vo = formDefinitionService.create(saveRequest("form-a", "A表单"));

        ArgumentCaptor<FormDefinition> captor = ArgumentCaptor.forClass(FormDefinition.class);
        verify(formDefinitionMapper).insert(captor.capture());
        assertEquals("form-a", captor.getValue().getCode());
        assertEquals("A表单", captor.getValue().getName());
        assertEquals(1, captor.getValue().getVersion().intValue());
        assertEquals(1, captor.getValue().getStatus().intValue());
        assertEquals("form-a", vo.getCode());
    }

    @Test
    @DisplayName("create：编码已存在时抛参数错误且不落库")
    void shouldRejectDuplicateCode() {
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinition>>any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> formDefinitionService.create(saveRequest("form-a", "A表单")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDefinitionMapper, never()).insert(any(FormDefinition.class));
    }

    @Test
    @DisplayName("create：schema_json 超过 64KB 抛参数错误且不落库")
    void shouldRejectOversizeSchema() {
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinition>>any())).thenReturn(0L);
        FormDefinitionSaveRequest request = saveRequest("form-a", "A表单");
        request.setSchemaJson("x".repeat(65537));

        BizException ex = assertThrows(BizException.class, () -> formDefinitionService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDefinitionMapper, never()).insert(any(FormDefinition.class));
    }

    @Test
    @DisplayName("update：存在时更新字段并返回视图对象")
    void shouldUpdateWhenExists() {
        FormDefinition entity = new FormDefinition();
        entity.setId(1L);
        entity.setCode("form-a");
        when(formDefinitionMapper.selectById(1L)).thenReturn(entity);
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfig>>any()))
                .thenReturn(List.of());
        FormDefinitionSaveRequest request = saveRequest("form-a", "A表单新版");
        request.setSchemaJson("{\"fields\":[]}");
        request.setRemark("备注");

        FormDefinitionVO vo = formDefinitionService.update(1L, request);

        verify(formDefinitionMapper).updateById(entity);
        assertEquals("A表单新版", entity.getName());
        assertEquals("{\"fields\":[]}", entity.getSchemaJson());
        assertEquals("备注", vo.getRemark());
    }

    @Test
    @DisplayName("update：ID 不存在抛 NOT_FOUND 且不更新")
    void shouldThrowWhenUpdateMissing() {
        when(formDefinitionMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> formDefinitionService.update(99L, saveRequest("form-a", "A表单")));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(formDefinitionMapper, never()).updateById(any(FormDefinition.class));
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
        FormDefinition entity = new FormDefinition();
        entity.setId(1L);
        entity.setCode("form-a");
        entity.setName("A表单");
        when(formDefinitionMapper.selectById(1L)).thenReturn(entity);
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfig>>any()))
                .thenReturn(List.of());

        FormDefinitionVO vo = formDefinitionService.getDetail(1L);

        assertEquals(1L, vo.getId());
        assertEquals("form-a", vo.getCode());
        assertEquals("A表单", vo.getName());
    }

    @Test
    @DisplayName("page：按条件调用分页查询并映射结果")
    void shouldPageWithConditions() {
        FormDefinition entity = new FormDefinition();
        entity.setId(1L);
        entity.setCode("form-a");
        entity.setName("A表单");
        Page<FormDefinition> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(formDefinitionMapper.selectPage(ArgumentMatchers.<Page<FormDefinition>>any(),
                ArgumentMatchers.<Wrapper<FormDefinition>>any())).thenReturn(page);
        FormQueryRequest request = new FormQueryRequest();
        request.setName("A");
        request.setCode("form-a");
        request.setStatus(1);

        PageResult<FormDefinitionVO> result = formDefinitionService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("form-a", result.getRecords().get(0).getCode());
        verify(formDefinitionMapper).selectPage(ArgumentMatchers.<Page<FormDefinition>>any(),
                ArgumentMatchers.<Wrapper<FormDefinition>>any());
    }

    @Test
    @DisplayName("create：元数据模型按 dto 持久化 colSpan（缺省 24）")
    void shouldPersistColSpanOnCreate() {
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinition>>any())).thenReturn(0L);
        FormTableDefinition table = new FormTableDefinition();
        table.setId(9L);
        when(formTableDefinitionMapper.selectById(9L)).thenReturn(table);
        FormColumnMeta column = new FormColumnMeta();
        column.setId(7L);
        column.setTableDefinitionId(9L);
        column.setColumnCode("guest_name");
        column.setColumnName("姓名");
        column.setDataType("text");
        column.setRequired(0);
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMeta>>any()))
                .thenReturn(List.of(column));
        when(formFieldOptionMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldOption>>any()))
                .thenReturn(List.of());
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfig>>any()))
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

        ArgumentCaptor<FormFieldConfig> captor = ArgumentCaptor.forClass(FormFieldConfig.class);
        verify(formFieldConfigMapper, times(2)).insert(captor.capture());
        assertEquals(12, captor.getAllValues().get(0).getColSpan().intValue());
        assertEquals(24, captor.getAllValues().get(1).getColSpan().intValue());
    }

    @Test
    @DisplayName("getDetail：元数据模型回填关联列信息（columnCode/dataType/required/maxLength/colSpan）")
    void shouldEnrichColumnInfoOnDetail() {
        FormDefinition entity = new FormDefinition();
        entity.setId(1L);
        entity.setCode("form-a");
        entity.setTableDefinitionId(9L);
        when(formDefinitionMapper.selectById(1L)).thenReturn(entity);
        FormFieldConfig config = new FormFieldConfig();
        config.setId(100L);
        config.setFormId(1L);
        config.setColumnMetaId(7L);
        config.setFieldLabel("姓名");
        config.setColSpan(12);
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfig>>any()))
                .thenReturn(List.of(config));
        when(formFieldOptionMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldOption>>any()))
                .thenReturn(List.of());
        FormColumnMeta column = new FormColumnMeta();
        column.setId(7L);
        column.setTableDefinitionId(9L);
        column.setColumnCode("guest_name");
        column.setDataType("text");
        column.setRequired(1);
        column.setLength(64);
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMeta>>any()))
                .thenReturn(List.of(column));

        FormDefinitionVO vo = formDefinitionService.getDetail(1L);

        assertEquals(1, vo.getFields().size());
        assertEquals("guest_name", vo.getFields().get(0).getColumnCode());
        assertEquals("text", vo.getFields().get(0).getDataType());
        assertEquals(1, vo.getFields().get(0).getRequired().intValue());
        assertEquals(64, vo.getFields().get(0).getMaxLength().intValue());
        assertEquals(12, vo.getFields().get(0).getColSpan().intValue());
        verify(formColumnMetaMapper).selectList(ArgumentMatchers.<Wrapper<FormColumnMeta>>any());
    }
}
