package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.form.entity.FormColumnMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 表单物理表 DDL 生成器。
 *
 * <p>表名规则为 {@code form_data_{code}_v{version}}；动态列类型可由 {@link FormField}（旧模型，来自
 * {@code schema_json}）或 {@link FormColumnMeta}（元数据模型，来自列元数据）映射得到。
 * 生成的 DDL 仅包含受校验的标识符（列名取自白名单），不含任何用户输入的直接拼接，故可在发布路径安全执行。</p>
 *
 * @author BlueWhale
 */
public final class FormTableDdlGenerator {

    /** 物理表名前缀 */
    public static final String TABLE_PREFIX = "form_data_";

    /** 可发布表单/表编码的正则（小写字母开头、总长 ≤40，保证物理表名 ≤64） */
    private static final Pattern PUBLISHABLE_CODE = Pattern.compile("^[a-z][a-z0-9_]{0,39}$");

    /** MySQL 标识符最大长度 */
    private static final int MAX_IDENTIFIER_LENGTH = 64;

    /** COMMENT 文本最大长度 */
    private static final int MAX_COMMENT_LENGTH = 200;

    /** 工具类不允许实例化 */
    private FormTableDdlGenerator() {
    }

    /**
     * 校验表单编码可作为物理表名（小写字母开头，仅小写字母/数字/下划线，总长 ≤40）
     *
     * @param code 表单定义编码
     * @throws BizException 编码不合规时抛出 {@code PARAM_ERROR}
     */
    public static void assertPublishableCode(String code) {
        if (code == null || !PUBLISHABLE_CODE.matcher(code).matches()) {
            throw paramError("表单编码仅允许小写字母、数字、下划线且以字母开头，长度不超过 40");
        }
    }

    /**
     * 生成物理表名并校验长度不超过 64 字符
     *
     * @param code 表单定义编码（或表编码）
     * @param version 版本
     * @return 物理表名 {@code form_data_{code}_v{version}}
     * @throws BizException 表名超长时抛出 {@code PARAM_ERROR}
     */
    public static String tableName(String code, Integer version) {
        String table = TABLE_PREFIX + code + "_v" + version;
        if (table.length() > MAX_IDENTIFIER_LENGTH) {
            throw paramError("物理表名超过 64 字符：" + table);
        }
        return table;
    }

    /**
     * 生成物理表建表语句（基于旧模型 schema 字段，{@code CREATE TABLE IF NOT EXISTS}）
     *
     * @param table 物理表名
     * @param code 编码（写入表注释）
     * @param version 版本（写入表注释）
     * @param fields 动态字段列表
     * @return 建表 DDL 文本
     */
    public static String generate(String table, String code, Integer version, List<FormField> fields) {
        List<String> defs = baseColumnDefs();
        for (FormField field : fields) {
            defs.add("`" + field.getKey() + "` " + columnType(field)
                    + " DEFAULT NULL COMMENT '" + escapeComment(field.getLabel()) + "'");
        }
        defs.addAll(baseIndexDefs());
        return buildDdl(table, "表单数据物理表 " + code + " v" + version, defs);
    }

    /**
     * 生成物理表建表语句（基于元数据模型列元数据，{@code CREATE TABLE IF NOT EXISTS}）
     *
     * @param table 物理表名
     * @param tableCode 表编码（写入表注释）
     * @param version 表版本（写入表注释）
     * @param columns 列元数据列表
     * @return 建表 DDL 文本
     * @throws BizException 列类型/参数非法时抛出 {@code PARAM_ERROR}
     */
    public static String generateFromColumns(String table, String tableCode, Integer version,
                                             List<FormColumnMeta> columns) {
        List<String> defs = baseColumnDefs();
        for (FormColumnMeta column : columns) {
            defs.add("`" + column.getColumnCode() + "` " + FormDataType.mysqlType(
                    column.getDataType(), column.getLength(), column.getPrecision(), column.getScale())
                    + " DEFAULT NULL COMMENT '" + escapeComment(column.getColumnName()) + "'");
        }
        defs.addAll(baseIndexDefs());
        return buildDdl(table, "表单数据物理表 " + tableCode + " v" + version, defs);
    }

    /**
     * 字段类型映射到 MySQL 列类型（旧模型类型白名单见 {@link FormSchemaParser}）
     *
     * @param field 字段定义
     * @return MySQL 列类型文本
     * @throws BizException 类型不在白名单时抛出 {@code PARAM_ERROR}
     */
    public static String columnType(FormField field) {
        switch (field.getType()) {
            case "text":
                Integer maxLength = field.getMaxLength();
                int length = maxLength == null ? FormSchemaParser.DEFAULT_TEXT_LENGTH : maxLength;
                return "VARCHAR(" + length + ")";
            case "textarea":
                return "TEXT";
            case "number":
                return "DECIMAL(18,4)";
            case "date":
                return "DATE";
            case "datetime":
                return "DATETIME";
            case "select":
                return "VARCHAR(255)";
            case "bool":
                return "TINYINT";
            default:
                throw paramError("字段 type 非法：" + field.getType());
        }
    }

    /**
     * 物理表公共列定义
     *
     * @return 公共列定义列表
     */
    private static List<String> baseColumnDefs() {
        List<String> defs = new ArrayList<>();
        defs.add("`id` BIGINT NOT NULL COMMENT '主键（= form_data 主表 id）'");
        defs.add("`tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID（行级隔离）'");
        defs.add("`definition_id` BIGINT NOT NULL COMMENT '所属表定义ID'");
        defs.add("`definition_version` INT NOT NULL COMMENT '表版本'");
        defs.add("`biz_key` VARCHAR(64) DEFAULT NULL COMMENT '业务键（可选）'");
        defs.add("`submitter` VARCHAR(64) DEFAULT NULL COMMENT '提交人账号'");
        defs.add("`status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 有效 / 0 作废'");
        defs.add("`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'");
        defs.add("`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP "
                + "ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'");
        defs.add("`deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除'");
        return defs;
    }

    /**
     * 物理表索引定义
     *
     * @return 索引定义列表
     */
    private static List<String> baseIndexDefs() {
        List<String> defs = new ArrayList<>();
        defs.add("PRIMARY KEY (`id`)");
        defs.add("KEY `idx_tenant_id` (`tenant_id`)");
        defs.add("KEY `idx_biz_key` (`biz_key`)");
        return defs;
    }

    /**
     * 组装建表 DDL 文本
     *
     * @param table 物理表名
     * @param comment 表注释（未转义）
     * @param defs 列/索引定义列表
     * @return 建表 DDL 文本
     */
    private static String buildDdl(String table, String comment, List<String> defs) {
        return "CREATE TABLE IF NOT EXISTS `" + table + "` (" + String.join(", ", defs)
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='"
                + escapeComment(comment) + "'";
    }

    /**
     * 转义注释文本中的单引号并截断超长内容
     *
     * @param text 原始注释文本，可为 {@code null}
     * @return 转义并截断后的注释文本
     */
    private static String escapeComment(String text) {
        String value = text == null ? "" : text.replace("'", "''").replace('\n', ' ').replace('\r', ' ');
        return value.length() > MAX_COMMENT_LENGTH ? value.substring(0, MAX_COMMENT_LENGTH) : value;
    }

    /**
     * 构造参数错误异常
     *
     * @param message 错误消息
     * @return 参数错误业务异常
     */
    private static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
    }
}
