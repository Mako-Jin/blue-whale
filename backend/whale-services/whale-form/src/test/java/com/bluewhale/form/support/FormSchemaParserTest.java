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
}
