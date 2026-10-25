package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.form.entity.FormColumnMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @DisplayName("tableName：按 code 与 version 生成表名，默认租户沿用历史命名")
    void shouldGenerateTableName() {
        assertEquals("form_data_fm_a_v2", FormTableDdlGenerator.tableName("fm_a", 2));
        assertEquals("form_data_fm_a_v2", FormTableDdlGenerator.tableName(1L, "fm_a", 2));
    }

    @Test
    @DisplayName("tableName：非默认租户物理表名带租户段")
    void shouldGenerateTenantScopedTableName() {
        assertEquals("form_data_t2_fm_a_v2", FormTableDdlGenerator.tableName(2L, "fm_a", 2));

        Long previous = TenantContext.get();
        try {
            TenantContext.set(7L);
            assertEquals("form_data_t7_fm_a_v2", FormTableDdlGenerator.tableName("fm_a", 2));
        } finally {
            TenantContext.set(previous);
        }
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

    @Test
    @DisplayName("columnDef：列名加反引号、含类型与 COMMENT")
    void shouldDefineColumn() {
        String def = FormTableDdlGenerator.columnDef(column("code", "名称", "text", 255));

        assertEquals("`code` VARCHAR(255) DEFAULT NULL COMMENT '名称'", def);
    }

    @Test
    @DisplayName("alterAdd / alterModify：前缀正确且以 columnDef 结尾")
    void shouldGenerateAddAndModifyAlter() {
        String table = "form_data_fm_a_v1";
        FormColumnMeta meta = column("remark", "备注", "text", 255);
        String def = FormTableDdlGenerator.columnDef(meta);

        assertEquals("ALTER TABLE `" + table + "` ADD COLUMN " + def,
                FormTableDdlGenerator.alterAdd(table, meta));
        assertEquals("ALTER TABLE `" + table + "` MODIFY COLUMN " + def,
                FormTableDdlGenerator.alterModify(table, meta));
    }

    @Test
    @DisplayName("alterDrop：生成删除列语句")
    void shouldGenerateDropAlter() {
        assertEquals("ALTER TABLE `form_data_fm_a_v1` DROP COLUMN `remark`",
                FormTableDdlGenerator.alterDrop("form_data_fm_a_v1", "remark"));
    }

    @Test
    @DisplayName("normalizeType：去反引号/空格并转小写，null 返回空串")
    void shouldNormalizeType() {
        assertEquals("varchar(255)", FormTableDdlGenerator.normalizeType("varchar(255)"));
        assertEquals("varchar(255)", FormTableDdlGenerator.normalizeType("VARCHAR(255) "));
        assertEquals("decimal(18,4)", FormTableDdlGenerator.normalizeType(" DECIMAL(18,4) "));
        assertEquals("varchar(255)", FormTableDdlGenerator.normalizeType("`varchar(255)`"));
        assertEquals("", FormTableDdlGenerator.normalizeType(null));
    }

    @Test
    @DisplayName("reservedColumns：含公共列、不含业务列")
    void shouldExposeReservedColumns() {
        Set<String> reserved = FormTableDdlGenerator.reservedColumns();

        assertTrue(reserved.contains("id"));
        assertTrue(reserved.contains("deleted"));
        assertFalse(reserved.contains("remark"));
        assertFalse(reserved.contains("amount"));
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

    /**
     * 构造列元数据桩对象。
     *
     * @param code 列编码
     * @param name 列名称
     * @param dataType 存储类型
     * @param length 长度
     * @return 列元数据
     */
    private static FormColumnMeta column(String code, String name, String dataType, Integer length) {
        FormColumnMeta column = new FormColumnMeta();
        column.setColumnCode(code);
        column.setColumnName(name);
        column.setDataType(dataType);
        column.setLength(length);
        return column;
    }
}
