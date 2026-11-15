package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.form.po.FormColumnMetaPo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    /** 与 {@code FormTableDdlGenerator} 内部一致的注释长度上限 */
    private static final int MAX_COMMENT_LENGTH = 200;

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
        List<FormColumnMetaPo> columns = new ArrayList<>();
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
        FormColumnMetaPo meta = column("amount", "金额", "number", null);
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

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("commentEscapeCases")
    @DisplayName("escapeComment：单引号双写闭合、反斜杠双写、超长截断与换行归一")
    void shouldEscapeCommentInGeneratedDdl(String caseName, String rawLabel, String expectedComment) {
        String def = FormTableDdlGenerator.columnDef(column("memo", rawLabel, "text", 255));
        String ddl = FormTableDdlGenerator.generate("form_data_fm_a_v1", "fm_a", 1,
                List.of(new FormField("memo", rawLabel, "text", false, 255)));

        assertEquals("`memo` VARCHAR(255) DEFAULT NULL COMMENT '" + expectedComment + "'", def);
        assertEquals(expectedComment, commentOf(def));
        assertTrue(ddl.contains("COMMENT '" + expectedComment + "'"));
        assertFalse(ddl.contains("\n"));
        assertFalse(ddl.contains("\r"));
    }

    @Test
    @DisplayName("generate：注入型注释仍被单引号包裹，DROP TABLE 不构成第二条可执行语句")
    void shouldKeepInjectionPayloadInsideCommentLiteral() {
        String payload = "x'; DROP TABLE form_data_fm_a_v1; --";
        String ddl = FormTableDdlGenerator.generate("form_data_fm_a_v1", "fm_a", 1,
                List.of(new FormField("memo", payload, "text", false, 255)));

        assertTrue(ddl.contains("''"));
        assertTrue(Pattern.compile("COMMENT '" + Pattern.quote("x''; DROP TABLE form_data_fm_a_v1; --") + "'")
                .matcher(ddl).find());
        assertTrue(ddl.chars().filter(ch -> ch == '\'').count() % 2 == 0,
                "单引号必须成对，注释内的分号不能越出字符串字面量");
    }

    @Test
    @DisplayName("columnDef：注释超过 200 字符时截断")
    void shouldTruncateOverlongComment() {
        String def = FormTableDdlGenerator.columnDef(
                column("memo", "长".repeat(MAX_COMMENT_LENGTH + 60), "text", 255));

        assertEquals(MAX_COMMENT_LENGTH, commentOf(def).length());
    }

    @Test
    @DisplayName("alterAdd / alterModify：前缀正确且以 columnDef 结尾")
    void shouldGenerateAddAndModifyAlter() {
        String table = "form_data_fm_a_v1";
        FormColumnMetaPo meta = column("remark", "备注", "text", 255);
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
     * 注释转义参数化用例：原始注释 → 期望写入 DDL 的注释文本。
     *
     * @return 用例参数流
     */
    private static Stream<Arguments> commentEscapeCases() {
        return Stream.of(
                Arguments.of("注入型注释：单引号双写并保持字面量闭合",
                        "x'; DROP TABLE form_data_fm_a_v1; --",
                        "x''; DROP TABLE form_data_fm_a_v1; --"),
                Arguments.of("以反斜杠结尾：反斜杠双写，不吞掉闭合引号",
                        "危险注释\\",
                        "危险注释\\\\"),
                Arguments.of("超长注释：截断到 " + MAX_COMMENT_LENGTH + " 字符",
                        "长".repeat(MAX_COMMENT_LENGTH + 60),
                        "长".repeat(MAX_COMMENT_LENGTH)),
                Arguments.of("含换行与回车：归一为空格，DDL 保持单行",
                        "第一行\n第二行\r结束",
                        "第一行 第二行 结束"));
    }

    /**
     * 提取列定义中 {@code COMMENT '...'} 的注释文本（columnDef 输出以注释结尾）。
     *
     * @param def 列定义文本
     * @return 注释文本
     */
    private static String commentOf(String def) {
        String prefix = "COMMENT '";
        int start = def.indexOf(prefix) + prefix.length();
        return def.substring(start, def.length() - 1);
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
    private static FormColumnMetaPo column(String code, String name, String dataType, Integer length) {
        FormColumnMetaPo column = new FormColumnMetaPo();
        column.setColumnCode(code);
        column.setColumnName(name);
        column.setDataType(dataType);
        column.setLength(length);
        return column;
    }
}
