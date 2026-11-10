package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.form.entity.FormColumnMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 字段级自动索引名前缀（仅管理此前缀的索引，不触碰其他索引） */
    public static final String AUTO_INDEX_PREFIX = "idx_f_";

    /** 单表自动索引数量上限（控制写放大，超出按列顺序截断） */
    public static final int MAX_AUTO_INDEXES = 16;

    /** 文本列可建全列索引的最大长度（utf8mb4 下 191*4=764 字节，兼容 767 字节索引前缀限制） */
    public static final int MAX_INDEXED_TEXT_LENGTH = 191;

    /** 物理表公共列（与 {@link FormSchemaParser} 保留列一致，永不参与增量变更） */
    private static final Set<String> RESERVED_COLUMNS = Set.of(
            "id", "tenant_id", "definition_id", "definition_version", "biz_key",
            "submitter", "status", "create_time", "update_time", "deleted");

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
     * 生成物理表名并校验长度不超过 64 字符（租户取自 {@link TenantContext}）
     *
     * <p>默认租户（1）沿用历史表名 {@code form_data_{code}_v{version}}，保证存量物理表无需重命名；
     * 其他租户使用 {@code form_data_t{tenantId}_{code}_v{version}}，避免不同租户同名表编码的列冲突。</p>
     *
     * @param code 表单定义编码（或表编码）
     * @param version 版本
     * @return 物理表名
     * @throws BizException 表名超长时抛出 {@code PARAM_ERROR}
     */
    public static String tableName(String code, Integer version) {
        return tableName(TenantContext.get(), code, version);
    }

    /**
     * 按指定租户生成物理表名并校验长度不超过 64 字符
     *
     * @param tenantId 租户ID（{@code null} 视为默认租户 1）
     * @param code 表单定义编码（或表编码）
     * @param version 版本
     * @return 物理表名
     * @throws BizException 表名超长时抛出 {@code PARAM_ERROR}
     */
    public static String tableName(Long tenantId, String code, Integer version) {
        long tenant = tenantId == null ? TenantContext.DEFAULT_TENANT_ID : tenantId;
        String tenantSegment = tenant == TenantContext.DEFAULT_TENANT_ID ? "" : "t" + tenant + "_";
        String table = TABLE_PREFIX + tenantSegment + code + "_v" + version;
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
        defs.addAll(autoIndexDefsForFields(fields));
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
            defs.add(columnDef(column));
        }
        defs.addAll(baseIndexDefs());
        defs.addAll(autoIndexDefs(columns));
        return buildDdl(table, "表单数据物理表 " + tableCode + " v" + version, defs);
    }

    /**
     * 单个动态列的定义片段（列名 + MySQL 类型 + 注释）
     *
     * @param column 列元数据
     * @return 形如 {@code `code` VARCHAR(255) DEFAULT NULL COMMENT '名称'} 的定义文本
     * @throws BizException 存储类型/参数非法时抛出 {@code PARAM_ERROR}
     */
    public static String columnDef(FormColumnMeta column) {
        return "`" + column.getColumnCode() + "` " + FormDataType.mysqlType(
                column.getDataType(), column.getLength(), column.getPrecision(), column.getScale())
                + " DEFAULT NULL COMMENT '" + escapeComment(column.getColumnName()) + "'";
    }

    /**
     * 生成新增列语句
     *
     * @param table 物理表名
     * @param column 待新增列元数据
     * @return {@code ALTER TABLE} 语句
     */
    public static String alterAdd(String table, FormColumnMeta column) {
        return "ALTER TABLE `" + table + "` ADD COLUMN " + columnDef(column);
    }

    /**
     * 生成修改列语句（类型/长度/精度/注释变更）
     *
     * @param table 物理表名
     * @param column 目标列元数据
     * @return {@code ALTER TABLE} 语句
     */
    public static String alterModify(String table, FormColumnMeta column) {
        return "ALTER TABLE `" + table + "` MODIFY COLUMN " + columnDef(column);
    }

    /**
     * 生成删除列语句
     *
     * @param table 物理表名
     * @param columnCode 待删除列编码（须已通过 {@link FormSchemaParser#assertColumnCode(String)} 校验）
     * @return {@code ALTER TABLE} 语句
     */
    public static String alterDrop(String table, String columnCode) {
        return "ALTER TABLE `" + table + "` DROP COLUMN `" + columnCode + "`";
    }

    /**
     * 判断列是否适合建字段级索引
     *
     * <p>仅高选择性/常用于过滤的标量类型（number/date/datetime/select）与短文本（长度 ≤
     * {@link #MAX_INDEXED_TEXT_LENGTH}）建索引；长文本、附件、子表与布尔（低区分度）不建。</p>
     *
     * @param dataType 存储类型
     * @param length 文本长度（仅 text 生效，可空视为 {@link FormDataType#DEFAULT_TEXT_LENGTH}）
     * @return 适合建索引返回 {@code true}
     */
    public static boolean isIndexable(String dataType, Integer length) {
        if (dataType == null) {
            return false;
        }
        switch (dataType) {
            case FormDataType.NUMBER:
            case FormDataType.DATE:
            case FormDataType.DATETIME:
            case FormDataType.SELECT:
                return true;
            case FormDataType.TEXT:
                int textLength = length == null ? FormDataType.DEFAULT_TEXT_LENGTH : length;
                return textLength <= MAX_INDEXED_TEXT_LENGTH;
            default:
                return false;
        }
    }

    /**
     * 字段级自动索引名（{@code idx_f_<列编码>}）
     *
     * @param columnCode 列编码
     * @return 索引名；列编码过长导致索引名超过 64 字符时返回 {@code null}（跳过该列索引）
     */
    public static String autoIndexName(String columnCode) {
        String name = AUTO_INDEX_PREFIX + columnCode;
        return name.length() > MAX_IDENTIFIER_LENGTH ? null : name;
    }

    /**
     * 单列自动索引定义片段（含数量上限截断）
     *
     * @param columnCode 列编码
     * @return 形如 {@code KEY `idx_f_x` (`x`)} 的定义文本；不可索引时返回 {@code null}
     */
    public static String autoIndexDef(String columnCode) {
        String name = autoIndexName(columnCode);
        return name == null ? null : "KEY `" + name + "` (`" + columnCode + "`)";
    }

    /**
     * 生成新增索引语句（发布时对齐存量物理表的字段级索引）
     *
     * @param table 物理表名
     * @param columnCode 列编码
     * @return {@code ALTER TABLE ... ADD INDEX} 语句
     */
    public static String alterAddIndex(String table, String columnCode) {
        String name = autoIndexName(columnCode);
        return "ALTER TABLE `" + table + "` ADD INDEX `" + (name == null ? AUTO_INDEX_PREFIX + columnCode : name)
                + "` (`" + columnCode + "`)";
    }

    /**
     * 生成删除索引语句（仅用于此前缀的自动索引，列已删除或不再满足索引条件时回收）
     *
     * @param table 物理表名
     * @param indexName 索引名（须以 {@link #AUTO_INDEX_PREFIX} 开头）
     * @return {@code ALTER TABLE ... DROP INDEX} 语句
     * @throws BizException 索引名非自动索引前缀时抛出 {@code PARAM_ERROR}
     */
    public static String alterDropIndex(String table, String indexName) {
        if (indexName == null || !indexName.startsWith(AUTO_INDEX_PREFIX)) {
            throw paramError("仅允许删除自动索引（前缀 " + AUTO_INDEX_PREFIX + "）：" + indexName);
        }
        return "ALTER TABLE `" + table + "` DROP INDEX `" + indexName + "`";
    }

    /**
     * 元数据模型下应存在的字段级索引定义列表（按列顺序，最多 {@link #MAX_AUTO_INDEXES} 个）
     *
     * @param columns 列元数据列表
     * @return 索引定义列表（不含不合格列）
     */
    public static List<String> autoIndexDefs(List<FormColumnMeta> columns) {
        List<String> defs = new ArrayList<>();
        for (FormColumnMeta column : columns) {
            if (defs.size() >= MAX_AUTO_INDEXES) {
                break;
            }
            if (!isIndexable(column.getDataType(), column.getLength())) {
                continue;
            }
            String def = autoIndexDef(column.getColumnCode());
            if (def != null) {
                defs.add(def);
            }
        }
        return defs;
    }

    /**
     * 旧模型（schema 字段）下应存在的字段级索引定义列表（按字段顺序，最多 {@link #MAX_AUTO_INDEXES} 个）
     *
     * @param fields 字段定义列表
     * @return 索引定义列表（不含不合格字段）
     */
    public static List<String> autoIndexDefsForFields(List<FormField> fields) {
        List<String> defs = new ArrayList<>();
        for (FormField field : fields) {
            if (defs.size() >= MAX_AUTO_INDEXES) {
                break;
            }
            if (!isIndexable(field.getType(), field.getMaxLength())) {
                continue;
            }
            String def = autoIndexDef(field.getKey());
            if (def != null) {
                defs.add(def);
            }
        }
        return defs;
    }

    /**
     * 元数据模型下应存在的自动索引名集合（与 {@link #autoIndexDefs(List)} 顺序一致）
     *
     * @param columns 列元数据列表
     * @return 索引名 → 列编码映射（保持列顺序）
     */
    public static Map<String, String> autoIndexes(List<FormColumnMeta> columns) {
        Map<String, String> indexes = new LinkedHashMap<>();
        for (FormColumnMeta column : columns) {
            if (indexes.size() >= MAX_AUTO_INDEXES) {
                break;
            }
            if (!isIndexable(column.getDataType(), column.getLength())) {
                continue;
            }
            String name = autoIndexName(column.getColumnCode());
            if (name != null) {
                indexes.put(name, column.getColumnCode());
            }
        }
        return indexes;
    }

    /**
     * 归一化 MySQL 列类型文本（用于与 {@link FormDataType#mysqlType} 输出比对差异）
     *
     * @param mysqlType 原始列类型文本（如 {@code varchar(255)}）
     * @return 小写、去空格、去反引号后的文本；入参为空时返回空串
     */
    public static String normalizeType(String mysqlType) {
        if (mysqlType == null) {
            return "";
        }
        return mysqlType.replace("`", "").replace(" ", "").toLowerCase();
    }

    /**
     * 物理表公共列集合（永不参与结构差异的 ADD / MODIFY / DROP）
     *
     * @return 公共列名集合
     */
    public static Set<String> reservedColumns() {
        return RESERVED_COLUMNS;
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
            case FormDataType.ATTACHMENT:
            case FormDataType.SUBTABLE:
                // 附件/子表值均为 JSON 数组，统一以 TEXT 承载
                return "TEXT";
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
