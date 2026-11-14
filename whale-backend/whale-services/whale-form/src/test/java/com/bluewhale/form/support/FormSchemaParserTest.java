package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormSchemaParser} 表单结构定义解析与白名单校验单元测试。
 *
 * @author BlueWhale
 */
class FormSchemaParserTest {

    private static final int PARAM_ERROR_CODE = 400;

    @Test
    @DisplayName("parse：合法 schema 解析出字段列表并回填 label/maxLength 默认值")
    void shouldParseValidSchema() {
        List<FormField> fields = FormSchemaParser.parse(
                "{\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\",\"required\":true},"
                        + "{\"key\":\"age\",\"type\":\"number\"}]}");

        assertEquals(2, fields.size());
        assertEquals("name", fields.get(0).getKey());
        assertEquals("姓名", fields.get(0).getLabel());
        assertTrue(fields.get(0).isRequired());
        assertEquals(255, fields.get(0).getMaxLength().intValue());
        assertEquals("age", fields.get(1).getKey());
        assertEquals("age", fields.get(1).getLabel());
    }

    @Test
    @DisplayName("parse：字段 key 非小写抛参数错误")
    void shouldThrowWhenKeyInvalid() {
        BizException ex = assertThrows(BizException.class,
                () -> FormSchemaParser.parse("{\"fields\":[{\"key\":\"Name\",\"type\":\"text\"}]}"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("parse：字段 key 命中保留列抛参数错误")
    void shouldThrowWhenKeyReserved() {
        BizException ex = assertThrows(BizException.class,
                () -> FormSchemaParser.parse("{\"fields\":[{\"key\":\"tenant_id\",\"type\":\"text\"}]}"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("parse：字段 key 重复抛参数错误")
    void shouldThrowWhenKeyDuplicated() {
        BizException ex = assertThrows(BizException.class,
                () -> FormSchemaParser.parse("{\"fields\":[{\"key\":\"name\",\"type\":\"text\"},"
                        + "{\"key\":\"name\",\"type\":\"text\"}]}"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("parse：字段 type 不在白名单抛参数错误")
    void shouldThrowWhenTypeInvalid() {
        BizException ex = assertThrows(BizException.class,
                () -> FormSchemaParser.parse("{\"fields\":[{\"key\":\"name\",\"type\":\"file\"}]}"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("parse：fields 缺失或为空抛参数错误")
    void shouldThrowWhenFieldsMissing() {
        assertThrows(BizException.class, () -> FormSchemaParser.parse("{\"fields\":[]}"));
        assertThrows(BizException.class, () -> FormSchemaParser.parse("{}"));
    }

    @Test
    @DisplayName("parse：schema 为空或非法 JSON 抛参数错误")
    void shouldThrowWhenSchemaInvalid() {
        assertThrows(BizException.class, () -> FormSchemaParser.parse(""));
        assertThrows(BizException.class, () -> FormSchemaParser.parse("not-json"));
    }

    @Test
    @DisplayName("parse：text maxLength 越界抛参数错误")
    void shouldThrowWhenMaxLengthInvalid() {
        BizException ex = assertThrows(BizException.class,
                () -> FormSchemaParser.parse("{\"fields\":[{\"key\":\"name\",\"type\":\"text\",\"maxLength\":5000}]}"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("parse：attachment 缺省 multiple=true、maxCount=0，并回填 accept")
    void shouldParseAttachmentWithDefaults() {
        List<FormField> fields = FormSchemaParser.parse(
                "{\"fields\":[{\"key\":\"files\",\"label\":\"附件\",\"type\":\"attachment\"},"
                        + "{\"key\":\"docs\",\"label\":\"资料\",\"type\":\"attachment\",\"multiple\":false,"
                        + "\"accept\":\".pdf\",\"maxCount\":3}]}");

        assertEquals(2, fields.size());
        assertEquals(Boolean.TRUE, fields.get(0).getMultiple());
        assertEquals(0, fields.get(0).getMaxCount().intValue());
        assertEquals(Boolean.FALSE, fields.get(1).getMultiple());
        assertEquals(".pdf", fields.get(1).getAccept());
        assertEquals(3, fields.get(1).getMaxCount().intValue());
    }

    @Test
    @DisplayName("parse：attachment maxCount 为负数抛参数错误")
    void shouldThrowWhenAttachmentMaxCountNegative() {
        BizException ex = assertThrows(BizException.class,
                () -> FormSchemaParser.parse("{\"fields\":[{\"key\":\"files\",\"type\":\"attachment\","
                        + "\"maxCount\":-1}]}"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("parse：subtable 解析 children/minRows/maxRows")
    void shouldParseSubtable() {
        List<FormField> fields = FormSchemaParser.parse(
                "{\"fields\":[{\"key\":\"lines\",\"label\":\"明细\",\"type\":\"subtable\",\"minRows\":1,"
                        + "\"maxRows\":5,\"children\":[{\"key\":\"product\",\"label\":\"商品\",\"type\":\"text\","
                        + "\"required\":true},{\"key\":\"amount\",\"label\":\"数量\",\"type\":\"number\"}]}]}");

        FormField subtable = fields.get(0);
        assertEquals(2, subtable.getChildren().size());
        assertEquals("product", subtable.getChildren().get(0).getKey());
        assertTrue(subtable.getChildren().get(0).isRequired());
        assertEquals("number", subtable.getChildren().get(1).getType());
        assertEquals(1, subtable.getMinRows().intValue());
        assertEquals(5, subtable.getMaxRows().intValue());
    }

    @Test
    @DisplayName("parse：subtable 子字段禁止嵌套子表")
    void shouldThrowWhenNestedSubtable() {
        BizException ex = assertThrows(BizException.class,
                () -> FormSchemaParser.parse("{\"fields\":[{\"key\":\"lines\",\"type\":\"subtable\","
                        + "\"children\":[{\"key\":\"inner\",\"type\":\"subtable\",\"children\":["
                        + "{\"key\":\"v\",\"type\":\"text\"}]}]}]}"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("不允许嵌套子表"));
    }

    @Test
    @DisplayName("parseStorage：subtable 无 children 时可解析（表级快照）")
    void shouldParseStorageWithoutSubtableChildren() {
        List<FormField> fields = FormSchemaParser.parseStorage(
                "{\"fields\":[{\"key\":\"lines\",\"label\":\"明细\",\"type\":\"subtable\",\"minRows\":1}]}");

        assertEquals(1, fields.size());
        assertEquals("subtable", fields.get(0).getType());
        assertTrue(fields.get(0).getChildren().isEmpty());
        assertEquals(1, fields.get(0).getMinRows().intValue());
    }

    @Test
    @DisplayName("parseStorage：仍校验字段 key/type 白名单")
    void shouldRejectInvalidFieldInStorageMode() {
        BizException ex = assertThrows(BizException.class,
                () -> FormSchemaParser.parseStorage("{\"fields\":[{\"key\":\"Name\",\"type\":\"text\"}]}"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("parse：subtable children 缺失抛参数错误")
    void shouldThrowWhenSubtableChildrenMissing() {
        BizException ex = assertThrows(BizException.class,
                () -> FormSchemaParser.parse("{\"fields\":[{\"key\":\"lines\",\"type\":\"subtable\"}]}"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("parse：subtable 子字段使用附件类型抛参数错误")
    void shouldThrowWhenSubtableChildAttachment() {
        BizException ex = assertThrows(BizException.class,
                () -> FormSchemaParser.parse("{\"fields\":[{\"key\":\"lines\",\"type\":\"subtable\","
                        + "\"children\":[{\"key\":\"files\",\"type\":\"attachment\"}]}]}"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }
}
