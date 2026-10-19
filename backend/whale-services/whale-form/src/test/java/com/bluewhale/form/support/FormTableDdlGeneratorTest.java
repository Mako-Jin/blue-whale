package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormTableDdlGenerator} 物理表命名、类型映射与 DDL 生成单元测试。
 *
 * @author BlueWhale
 */
class FormTableDdlGeneratorTest {

    private static final int PARAM_ERROR_CODE = 400;

    @Test
    @DisplayName("tableName：按 code 与 version 生成表名")
    void shouldGenerateTableName() {
        assertEquals("form_data_fm_a_v2", FormTableDdlGenerator.tableName("fm_a", 2));
    }

    @Test
    @DisplayName("assertPublishableCode：合法编码通过、非法编码抛参数错误")
    void shouldValidateCode() {
        FormTableDdlGenerator.assertPublishableCode("fm_a");
        BizException ex = assertThrows(BizException.class,
                () -> FormTableDdlGenerator.assertPublishableCode("Fm_A"));
        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("columnType：字段类型映射到 MySQL 列类型")
    void shouldMapColumnType() {
        assertEquals("VARCHAR(64)", FormTableDdlGenerator.columnType(field("text", 64)));
        assertEquals("VARCHAR(255)", FormTableDdlGenerator.columnType(field("text", null)));
        assertEquals("TEXT", FormTableDdlGenerator.columnType(field("textarea", null)));
        assertEquals("DECIMAL(18,4)", FormTableDdlGenerator.columnType(field("number", null)));
        assertEquals("DATE", FormTableDdlGenerator.columnType(field("date", null)));
        assertEquals("DATETIME", FormTableDdlGenerator.columnType(field("datetime", null)));
        assertEquals("VARCHAR(255)", FormTableDdlGenerator.columnType(field("select", null)));
        assertEquals("TINYINT", FormTableDdlGenerator.columnType(field("bool", null)));
    }

    @Test
    @DisplayName("generate：DDL 含公共列、动态列、主键与租户索引")
    void shouldGenerateDdl() {
        String ddl = FormTableDdlGenerator.generate("form_data_fm_a_v1", "fm_a", 1,
                List.of(field("text", 64)));

        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS `form_data_fm_a_v1`"));
        assertTrue(ddl.contains("`tenant_id` BIGINT NOT NULL DEFAULT 1"));
        assertTrue(ddl.contains("`name` VARCHAR(64) DEFAULT NULL"));
        assertTrue(ddl.contains("PRIMARY KEY (`id`)"));
        assertTrue(ddl.contains("KEY `idx_tenant_id` (`tenant_id`)"));
    }

    /**
     * 构造字段定义桩对象。
     *
     * @param type 字段类型
     * @param maxLength 文本长度
     * @return 字段定义
     */
    private static FormField field(String type, Integer maxLength) {
        return new FormField("name", "姓名", type, false, maxLength);
    }
}
