package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.form.entity.FormColumnMeta;
import com.bluewhale.form.entity.FormFieldConfig;
import com.bluewhale.form.entity.FormFieldOption;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormMetadataCompiler} 元数据 → {@code schema_json} 快照编译单元测试。
 *
 * @author BlueWhale
 */
class FormMetadataCompilerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 构造列元数据桩对象。
     *
     * @param id 列ID
     * @param code 列编码
     * @param name 列名称
     * @param dataType 存储类型
     * @param length 长度
     * @param required 是否必填
     * @return 列元数据
     */
    private static FormColumnMeta column(Long id, String code, String name, String dataType, Integer length,
                                         Integer required) {
        FormColumnMeta column = new FormColumnMeta();
        column.setId(id);
        column.setColumnCode(code);
        column.setColumnName(name);
        column.setDataType(dataType);
        column.setLength(length);
        column.setRequired(required);
        return column;
    }

    /**
     * 构造字段配置桩对象。
     *
     * @param id 字段配置ID
     * @param columnMetaId 关联列ID（可空）
     * @param label 显示名
     * @param widgetType 控件类型
     * @param showInForm 表单显示
     * @return 字段配置
     */
    private static FormFieldConfig config(Long id, Long columnMetaId, String label, String widgetType,
                                          Integer showInForm) {
        FormFieldConfig config = new FormFieldConfig();
        config.setId(id);
        config.setColumnMetaId(columnMetaId);
        config.setFieldLabel(label);
        config.setWidgetType(widgetType);
        config.setShowInForm(showInForm);
        return config;
    }

    /**
     * 构造选项桩对象。
     *
     * @param value 选项值
     * @return 选项实体
     */
    private static FormFieldOption option(String value) {
        FormFieldOption option = new FormFieldOption();
        option.setOptionValue(value);
        return option;
    }

    @Test
    @DisplayName("compile：按字段配置编译出 key/label/type/widget/required/maxLength/options")
    void shouldCompileFields() throws Exception {
        FormColumnMeta nameColumn = column(1L, "name", "姓名", "text", 50, 1);
        FormColumnMeta statusColumn = column(2L, "status", "状态", "select", null, 0);
        FormFieldConfig nameConfig = config(11L, 1L, "姓名", "text", 1);
        FormFieldConfig statusConfig = config(12L, 2L, "状态", "radio", 1);
        Map<Long, FormColumnMeta> columns = Map.of(1L, nameColumn, 2L, statusColumn);
        Map<Long, List<FormFieldOption>> options = Map.of(12L, List.of(option("A"), option("B")));

        String json = FormMetadataCompiler.compile(List.of(nameConfig, statusConfig), columns, options);

        JsonNode fields = MAPPER.readTree(json).get(FormSchemaParser.KEY_FIELDS);
        assertEquals(2, fields.size());
        assertEquals("name", fields.get(0).get("key").asText());
        assertEquals("text", fields.get(0).get("widget").asText());
        assertTrue(fields.get(0).get("required").asBoolean());
        assertEquals(50, fields.get(0).get("maxLength").asInt());
        assertEquals("radio", fields.get(1).get("widget").asText());
        assertEquals("A", fields.get(1).get("options").get(0).asText());
    }

    @Test
    @DisplayName("compile：仅布局字段（columnMetaId 为空）抛参数错误")
    void shouldThrowWhenOnlyLayoutFields() {
        FormFieldConfig layout = config(11L, null, "标题", null, 1);

        BizException ex = assertThrows(BizException.class,
                () -> FormMetadataCompiler.compile(List.of(layout), Map.of(), Map.of()));

        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("compile：同一表单重复引用同一列抛参数错误")
    void shouldThrowWhenDuplicateColumn() {
        FormColumnMeta column = column(1L, "name", "姓名", "text", 50, 0);
        FormFieldConfig first = config(11L, 1L, "姓名", "text", 1);
        FormFieldConfig second = config(12L, 1L, "姓名2", "text", 1);

        BizException ex = assertThrows(BizException.class,
                () -> FormMetadataCompiler.compile(List.of(first, second), Map.of(1L, column), Map.of()));

        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("compile：合法 linkage 原样写入 schema 字段节点")
    void shouldWriteLinkageNode() throws Exception {
        FormColumnMeta nameColumn = column(1L, "name", "姓名", "text", 50, 0);
        FormColumnMeta statusColumn = column(2L, "status", "状态", "select", null, 0);
        FormFieldConfig nameConfig = config(11L, 1L, "姓名", "text", 1);
        nameConfig.setValidateJson("{\"linkage\":{\"action\":\"show\",\"logic\":\"or\","
                + "\"conditions\":[{\"field\":\"status\",\"operator\":\"eq\",\"value\":\"A\"}]}}");
        FormFieldConfig statusConfig = config(12L, 2L, "状态", "select", 1);

        String json = FormMetadataCompiler.compile(List.of(nameConfig, statusConfig),
                Map.of(1L, nameColumn, 2L, statusColumn), Map.of());

        JsonNode linkage = MAPPER.readTree(json).get("fields").get(0).get("linkage");
        assertEquals("show", linkage.get("action").asText());
        assertEquals("or", linkage.get("logic").asText());
        assertEquals("status", linkage.get("conditions").get(0).get("field").asText());
    }

    @Test
    @DisplayName("compile：linkage 动作非法抛参数错误")
    void shouldRejectInvalidLinkageAction() {
        FormColumnMeta nameColumn = column(1L, "name", "姓名", "text", 50, 0);
        FormFieldConfig nameConfig = config(11L, 1L, "姓名", "text", 1);
        nameConfig.setValidateJson("{\"linkage\":{\"action\":\"toggle\","
                + "\"conditions\":[{\"field\":\"name\",\"operator\":\"eq\",\"value\":\"A\"}]}}");

        BizException ex = assertThrows(BizException.class, () -> FormMetadataCompiler.compile(
                List.of(nameConfig), Map.of(1L, nameColumn), Map.of()));

        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("compile：linkage 引用不存在的字段抛参数错误")
    void shouldRejectLinkageUnknownField() {
        FormColumnMeta nameColumn = column(1L, "name", "姓名", "text", 50, 0);
        FormFieldConfig nameConfig = config(11L, 1L, "姓名", "text", 1);
        nameConfig.setValidateJson("{\"linkage\":{\"action\":\"hide\","
                + "\"conditions\":[{\"field\":\"missing\",\"operator\":\"empty\"}]}}");

        BizException ex = assertThrows(BizException.class, () -> FormMetadataCompiler.compile(
                List.of(nameConfig), Map.of(1L, nameColumn), Map.of()));

        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("compile：linkage 自引用抛参数错误")
    void shouldRejectLinkageSelfReference() {
        FormColumnMeta nameColumn = column(1L, "name", "姓名", "text", 50, 0);
        FormFieldConfig nameConfig = config(11L, 1L, "姓名", "text", 1);
        nameConfig.setValidateJson("{\"linkage\":{\"action\":\"disable\","
                + "\"conditions\":[{\"field\":\"name\",\"operator\":\"notEmpty\"}]}}");

        BizException ex = assertThrows(BizException.class, () -> FormMetadataCompiler.compile(
                List.of(nameConfig), Map.of(1L, nameColumn), Map.of()));

        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("compile：linkage 的 in 取值为空抛参数错误")
    void shouldRejectLinkageEmptyInValue() {
        FormColumnMeta nameColumn = column(1L, "name", "姓名", "text", 50, 0);
        FormColumnMeta statusColumn = column(2L, "status", "状态", "select", null, 0);
        FormFieldConfig nameConfig = config(11L, 1L, "姓名", "text", 1);
        nameConfig.setValidateJson("{\"linkage\":{\"action\":\"require\","
                + "\"conditions\":[{\"field\":\"status\",\"operator\":\"in\",\"value\":\"\"}]}}");
        FormFieldConfig statusConfig = config(12L, 2L, "状态", "select", 1);

        BizException ex = assertThrows(BizException.class, () -> FormMetadataCompiler.compile(
                List.of(nameConfig, statusConfig), Map.of(1L, nameColumn, 2L, statusColumn), Map.of()));

        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("compile：linkage 条件含未知键抛参数错误")
    void shouldRejectLinkageUnknownConditionKey() {
        FormColumnMeta nameColumn = column(1L, "name", "姓名", "text", 50, 0);
        FormColumnMeta statusColumn = column(2L, "status", "状态", "select", null, 0);
        FormFieldConfig nameConfig = config(11L, 1L, "姓名", "text", 1);
        nameConfig.setValidateJson("{\"linkage\":{\"action\":\"show\","
                + "\"conditions\":[{\"field\":\"status\",\"operator\":\"eq\",\"value\":\"A\",\"extra\":1}]}}");
        FormFieldConfig statusConfig = config(12L, 2L, "状态", "select", 1);

        BizException ex = assertThrows(BizException.class, () -> FormMetadataCompiler.compile(
                List.of(nameConfig, statusConfig), Map.of(1L, nameColumn, 2L, statusColumn), Map.of()));

        assertEquals(400, ex.getCode());
    }
}
