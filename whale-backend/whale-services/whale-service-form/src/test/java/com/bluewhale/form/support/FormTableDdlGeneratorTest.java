package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.form.entity.FormColumnMetaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    @DisplayName("generate：可索引字段自动生成 idx_f_ 索引，长文本与布尔不建索引")
    void shouldGenerateAutoIndexesInDdl() {
        String ddl = FormTableDdlGenerator.generate("form_data_fm_b_v1", "fm_b", 1,
                List.of(field("amount", "number", null),
                        field("code", "text", 64),
                        field("remark", "text", 500),
                        field("enabled", "bool", null),
                        field("detail", "textarea", null)));

        assertTrue(ddl.contains("KEY `idx_f_amount` (`amount`)"));
        assertTrue(ddl.contains("KEY `idx_f_code` (`code`)"));
        assertFalse(ddl.contains("idx_f_remark"));
        assertFalse(ddl.contains("idx_f_enabled"));
        assertFalse(ddl.contains("idx_f_detail"));
    }

    @Test
    @DisplayName("autoIndexes：按列顺序采集、跳过不可索引列、上限 16 个")
    void shouldCollectAutoIndexes() {
        List<FormColumnMetaEntity> columns = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            columns.add(column("n" + i, "数值" + i, "number", null));
        }
        columns.add(column("memo", "备注", "textarea", null));

        Map<String, String> indexes = FormTableDdlGenerator.autoIndexes(columns);

        assertEquals(16, indexes.size());
        assertEquals("n0", indexes.get("idx_f_n0"));
        assertFalse(indexes.containsKey("idx_f_memo"));
    }

    @Test
    @DisplayName("autoIndexes：text 长度超过 191 不建索引，select/date 建索引")
    void shouldFilterIndexableTypes() {
        Map<String, String> indexes = FormTableDdlGenerator.autoIndexes(List.of(
                column("code", "编码", "text", 191),
                column("long_code", "长编码", "text", 192),
                column("stage", "阶段", "select", null),
                column("due_date", "截止日", "date", null),
                column("created", "创建时间", "datetime", null),
                column("flag", "标记", "bool", null)));

        assertEquals(Set.of("idx_f_code", "idx_f_stage", "idx_f_due_date", "idx_f_created"),
                indexes.keySet());
    }

    @Test
    @DisplayName("alterAddIndex / alterDropIndex：仅允许回收自动索引")
    void shouldGenerateIndexAlter() {
        assertEquals("ALTER TABLE `form_data_fm_a_v1` ADD INDEX `idx_f_amount` (`amount`)",
                FormTableDdlGenerator.alterAddIndex("form_data_fm_a_v1", "amount"));
        assertEquals("ALTER TABLE `form_data_fm_a_v1` DROP INDEX `idx_f_legacy`",
                FormTableDdlGenerator.alterDropIndex("form_data_fm_a_v1", "idx_f_legacy"));

        BizException ex = assertThrows(BizException.class,
                () -> FormTableDdlGenerator.alterDropIndex("form_data_fm_a_v1", "idx_biz_key"));
        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("columnDef：列名加反引号、含类型与 COMMENT")
    void shouldDefineColumn() {
        String def = FormTableDdlGenerator.columnDef(column("code", "名称", "text", 255));

        assertEquals("`code` VARCHAR(255) DEFAULT NULL COMMENT '名称'", def);
    }

    @Test
    @DisplayName("columnDef：nullable=0 生成 NOT NULL，与服务层必填校验对齐")
    void shouldDefineNotNullColumn() {
        FormColumnMetaEntity meta = column("amount", "金额", "number", null);
        meta.setNullable(0);

        assertEquals("`amount` DECIMAL(18,4) NOT NULL COMMENT '金额'",
                FormTableDdlGenerator.columnDef(meta));
    }

    @Test
    @DisplayName("columnDef：注释中的反斜杠与单引号均被转义，避免 DDL 越界")
    void shouldEscapeComment() {
        assertEquals("`path` VARCHAR(255) DEFAULT NULL COMMENT '路径\\\\'",
                FormTableDdlGenerator.columnDef(column("path", "路径\\", "text", 255)));
        assertEquals("`memo` VARCHAR(255) DEFAULT NULL COMMENT 'a\\\\''b'",
                FormTableDdlGenerator.columnDef(column("memo", "a\\'b", "text", 255)));
    }

    @Test
    @DisplayName("alterAdd / alterModify：前缀正确且以 columnDef 结尾")
    void shouldGenerateAddAndModifyAlter() {
        String table = "form_data_fm_a_v1";
        FormColumnMetaEntity meta = column("remark", "备注", "text", 255);
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
     * 构造指定 key 的字段定义桩对象。
     *
     * @param key 字段 key
     * @param type 字段类型
     * @param maxLength 文本长度
     * @return 字段定义
     */
    private static FormField field(String key, String type, Integer maxLength) {
        return new FormField(key, key, type, false, maxLength);
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
    private static FormColumnMetaEntity column(String code, String name, String dataType, Integer length) {
        FormColumnMetaEntity column = new FormColumnMetaEntity();
        column.setColumnCode(code);
        column.setColumnName(name);
        column.setDataType(dataType);
        column.setLength(length);
        return column;
    }
}
