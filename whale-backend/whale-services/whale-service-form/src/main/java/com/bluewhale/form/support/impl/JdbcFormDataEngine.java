package com.bluewhale.form.support.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.form.model.dto.FormDataFilter;
import com.bluewhale.form.po.FormDataPo;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormDataType;
import com.bluewhale.form.support.FormField;
import com.bluewhale.form.support.FormTableDdlGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于 {@link JdbcTemplate} 的表单数据动态数据引擎实现。
 *
 * <p>表名与列名均做白名单校验（表名二次正则、列名来自 schema 解析白名单），值一律占位参数化绑定，
 * 杜绝 SQL 注入（spec §三.3.9）。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class JdbcFormDataEngine implements FormDataEngine {

    /** 物理表名二次校验正则 */
    private static final Pattern TABLE_PATTERN = Pattern.compile("^form_data_[a-z0-9_]+_v[0-9]+$");

    /** 日期值格式（{@code yyyy-MM-dd}） */
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** 日期时间值格式（{@code yyyy-MM-dd HH:mm} 或 {@code yyyy-MM-dd HH:mm:ss}） */
    private static final Pattern DATETIME_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(:\\d{2})?$");

    /** 操作符缺省值 */
    private static final String OP_EQ = "eq";

    /** 全部合法操作符 */
    private static final Set<String> FILTER_OPS = Set.of(OP_EQ, "ne", "gt", "gte", "lt", "lte",
            "like", "in", "between", "isNull", "notNull");

    /** 文本类列允许的操作符 */
    private static final Set<String> TEXT_OPS = Set.of(OP_EQ, "ne", "like", "in");

    /** 可比较类列（数值/日期）允许的操作符 */
    private static final Set<String> COMPARABLE_OPS = Set.of(OP_EQ, "ne", "gt", "gte", "lt", "lte", "in", "between");

    /** 布尔列允许的操作符 */
    private static final Set<String> BOOL_OPS = Set.of(OP_EQ, "ne", "in");

    /** in 条件值数量上限 */
    private static final int MAX_IN_VALUES = 50;

    /** JSON 解析器（附件/子表数组值序列化与反序列化） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void executeDdl(String ddl) {
        jdbcTemplate.execute(ddl);
    }

    @Override
    public Map<String, String> describeColumns(String table) {
        String target = assertTable(table);
        String sql = "SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        Map<String, String> columns = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, target);
        for (Map<String, Object> row : rows) {
            Object name = row.get("COLUMN_NAME");
            Object type = row.get("COLUMN_TYPE");
            if (name != null && type != null) {
                columns.put(name.toString(), FormTableDdlGenerator.normalizeType(type.toString()));
            }
        }
        return columns;
    }

    @Override
    public Set<String> describeIndexes(String table) {
        String target = assertTable(table);
        String sql = "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        return new LinkedHashSet<>(jdbcTemplate.queryForList(sql, String.class, target));
    }

    @Override
    public void insert(String table, FormDataPo record, List<FormField> fields, Map<String, Object> data) {
        String target = assertTable(table);
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        columns.add("id");
        values.add(record.getId());
        columns.add("tenant_id");
        values.add(record.getTenantId());
        columns.add("definition_id");
        values.add(record.getTableDefinitionId() != null ? record.getTableDefinitionId() : record.getDefinitionId());
        columns.add("definition_version");
        values.add(record.getTableVersion() != null ? record.getTableVersion() : record.getDefinitionVersion());
        columns.add("biz_key");
        values.add(record.getBizKey());
        columns.add("submitter");
        values.add(record.getSubmitter());
        columns.add("status");
        values.add(record.getStatus());
        for (FormField field : fields) {
            columns.add(field.getKey());
            values.add(toJdbcValue(data.get(field.getKey())));
        }
        String sql = "INSERT INTO `" + target + "` (" + quoteColumns(columns) + ") VALUES ("
                + placeholders(columns.size()) + ")";
        jdbcTemplate.update(sql, values.toArray());
    }

    @Override
    public void update(String table, FormDataPo record, List<FormField> fields, Map<String, Object> data) {
        String target = assertTable(table);
        if (fields.isEmpty()) {
            return;
        }
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (FormField field : fields) {
            columns.add(field.getKey());
            values.add(toJdbcValue(data.get(field.getKey())));
        }
        values.add(record.getId());
        values.add(record.getTenantId());
        String sql = "UPDATE `" + target + "` SET " + assignments(columns)
                + " WHERE `id` = ? AND `tenant_id` = ? AND `deleted` = 0";
        jdbcTemplate.update(sql, values.toArray());
    }

    @Override
    public Map<String, Object> selectFields(String table, Long id, Long tenantId, List<FormField> fields) {
        String target = assertTable(table);
        if (fields.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<String> columns = fields.stream().map(FormField::getKey).toList();
        String sql = "SELECT " + quoteColumns(columns) + " FROM `" + target
                + "` WHERE `id` = ? AND `tenant_id` = ? AND `deleted` = 0";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id, tenantId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            result.putAll(rows.get(0));
        }
        for (FormField field : fields) {
            if (result.containsKey(field.getKey())) {
                result.put(field.getKey(), fromJdbcValue(field, result.get(field.getKey())));
            }
        }
        return result;
    }

    @Override
    public long countByFilters(String table, Long tenantId, List<FormField> fields,
                               List<FormDataFilter> filters, String bizKey, String submitter) {
        String target = assertTable(table);
        List<Object> params = new ArrayList<>();
        String from = filterFrom(target, tenantId, fields, filters, bizKey, submitter, params);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + from, Long.class, params.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public List<Long> selectIdsByFilters(String table, Long tenantId, List<FormField> fields,
                                         List<FormDataFilter> filters, String bizKey, String submitter,
                                         int limit) {
        String target = assertTable(table);
        List<Object> params = new ArrayList<>();
        String from = filterFrom(target, tenantId, fields, filters, bizKey, submitter, params);
        String sql = "SELECT `id` FROM " + from + " ORDER BY `create_time` DESC, `id` DESC LIMIT ?";
        params.add(Math.max(limit, 1));
        return jdbcTemplate.queryForList(sql, Long.class, params.toArray());
    }

    @Override
    public List<Long> listIds(String table, Long tenantId, int limit) {
        String target = assertTable(table);
        String sql = "SELECT `id` FROM `" + target + "` WHERE `tenant_id` = ? AND `deleted` = 0"
                + " ORDER BY `id` LIMIT ?";
        return jdbcTemplate.queryForList(sql, Long.class, tenantId, Math.max(limit, 1));
    }

    @Override
    public void logicDelete(String table, Long id, Long tenantId) {
        String target = assertTable(table);
        jdbcTemplate.update("UPDATE `" + target + "` SET `deleted` = 1 WHERE `id` = ? AND `tenant_id` = ?",
                id, tenantId);
    }

    /**
     * 写库前把附件/子表的数组/对象值序列化为 JSON 文本（其余标量原样返回）
     *
     * @param value 字段值
     * @return JDBC 可绑定的值
     * @throws BizException 序列化失败时抛出 {@code PARAM_ERROR}
     */
    private static Object toJdbcValue(Object value) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
            try {
                return MAPPER.writeValueAsString(value);
            } catch (Exception e) {
                throw new BizException(ErrorCode.PARAM_ERROR, "form.data.attachment-serialize-failed",
                        "附件/子表数据序列化失败");
            }
        }
        return value;
    }

    /**
     * 读库后把附件/子表的 JSON 文本还原为数组（解析失败保留原始文本）
     *
     * @param field 字段定义
     * @param value 物理表原始值
     * @return 还原后的值
     */
    private static Object fromJdbcValue(FormField field, Object value) {
        boolean jsonType = FormDataType.ATTACHMENT.equals(field.getType())
                || FormDataType.SUBTABLE.equals(field.getType());
        if (!jsonType || !(value instanceof String text) || !StringUtils.hasText(text)) {
            return value;
        }
        try {
            return MAPPER.readValue(text, Object.class);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 构造动态过滤查询的 {@code `表` WHERE ...} 片段（租户、逻辑删除、业务键、提交人与动态条件）
     *
     * @param table 已校验的物理表名
     * @param tenantId 租户ID
     * @param fields 字段定义（列名白名单与类型依据）
     * @param filters 动态过滤条件
     * @param bizKey 业务键（可空）
     * @param submitter 提交人（可空）
     * @param params SQL 参数收集器（按占位符顺序追加）
     * @return FROM 片段
     */
    private static String filterFrom(String table, Long tenantId, List<FormField> fields,
                                     List<FormDataFilter> filters, String bizKey, String submitter,
                                     List<Object> params) {
        StringBuilder sql = new StringBuilder("`").append(table)
                .append("` WHERE `tenant_id` = ? AND `deleted` = 0");
        params.add(tenantId);
        if (StringUtils.hasText(bizKey)) {
            sql.append(" AND `biz_key` = ?");
            params.add(bizKey);
        }
        if (StringUtils.hasText(submitter)) {
            sql.append(" AND `submitter` = ?");
            params.add(submitter);
        }
        appendFilterConditions(fields, filters, sql, params);
        return sql.toString();
    }

    /**
     * 追加动态字段条件（字段名取自 schema 白名单、操作符白名单、值全部参数化绑定）
     *
     * @param fields 字段定义
     * @param filters 动态过滤条件
     * @param sql SQL 构造器
     * @param params SQL 参数收集器
     * @throws BizException 字段不存在、类型不支持、操作符非法或值非法时抛出 {@code PARAM_ERROR}
     */
    private static void appendFilterConditions(List<FormField> fields, List<FormDataFilter> filters,
                                               StringBuilder sql, List<Object> params) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        Map<String, FormField> fieldMap = new LinkedHashMap<>();
        for (FormField field : fields) {
            fieldMap.put(field.getKey(), field);
        }
        for (FormDataFilter filter : filters) {
            String fieldKey = filter.getField();
            FormField field = fieldMap.get(fieldKey);
            if (field == null) {
                throw paramError("过滤字段不在表结构定义中：" + fieldKey);
            }
            if (!FormDataType.isFilterable(field.getType())) {
                throw paramError("字段 " + labelOf(field) + " 不支持条件检索");
            }
            String op = normalizeOp(filter.getOp());
            assertOpAllowed(field, op);
            appendCondition(field, fieldKey, op, filter.getValue(), sql, params);
        }
    }

    /**
     * 追加单个字段条件（按操作符分派）
     *
     * @param field 字段定义
     * @param fieldKey 字段 key（已通过 schema 白名单校验）
     * @param op 已归一化的操作符
     * @param value 条件值
     * @param sql SQL 构造器
     * @param params SQL 参数收集器
     * @throws BizException 值非法时抛出 {@code PARAM_ERROR}
     */
    private static void appendCondition(FormField field, String fieldKey, String op, Object value,
                                        StringBuilder sql, List<Object> params) {
        switch (op) {
            case "isNull":
                sql.append(" AND `").append(fieldKey).append("` IS NULL");
                break;
            case "notNull":
                sql.append(" AND `").append(fieldKey).append("` IS NOT NULL");
                break;
            case "like":
                sql.append(" AND `").append(fieldKey).append("` LIKE ? ESCAPE '!'");
                params.add("%" + escapeLike(scalarValue(field, value)) + "%");
                break;
            case "in":
                appendInCondition(field, fieldKey, value, sql, params);
                break;
            case "between":
                appendBetweenCondition(field, fieldKey, value, sql, params);
                break;
            default:
                sql.append(" AND `").append(fieldKey).append("` ").append(sqlOperator(op)).append(" ?");
                params.add(coerce(field, scalarValue(field, value)));
                break;
        }
    }

    /**
     * 追加 {@code IN} 条件（值数量 1..{@value #MAX_IN_VALUES}）
     *
     * @param field 字段定义
     * @param fieldKey 字段 key
     * @param value 条件值（数组）
     * @param sql SQL 构造器
     * @param params SQL 参数收集器
     * @throws BizException 值非数组或数量越界时抛出 {@code PARAM_ERROR}
     */
    private static void appendInCondition(FormField field, String fieldKey, Object value,
                                          StringBuilder sql, List<Object> params) {
        List<Object> values = listValue(field, value);
        if (values.isEmpty() || values.size() > MAX_IN_VALUES) {
            throw paramError("字段 " + labelOf(field) + " 的 in 条件值数量需在 1.." + MAX_IN_VALUES);
        }
        sql.append(" AND `").append(fieldKey).append("` IN (").append(placeholders(values.size())).append(')');
        for (Object item : values) {
            params.add(coerce(field, item));
        }
    }

    /**
     * 追加 {@code BETWEEN} 条件（必须恰好 2 个值）
     *
     * @param field 字段定义
     * @param fieldKey 字段 key
     * @param value 条件值（数组）
     * @param sql SQL 构造器
     * @param params SQL 参数收集器
     * @throws BizException 值非数组或数量不为 2 时抛出 {@code PARAM_ERROR}
     */
    private static void appendBetweenCondition(FormField field, String fieldKey, Object value,
                                               StringBuilder sql, List<Object> params) {
        List<Object> range = listValue(field, value);
        if (range.size() != 2) {
            throw paramError("字段 " + labelOf(field) + " 的 between 条件需要 2 个值");
        }
        sql.append(" AND `").append(fieldKey).append("` BETWEEN ? AND ?");
        params.add(coerce(field, range.get(0)));
        params.add(coerce(field, range.get(1)));
    }

    /**
     * 归一化操作符（缺省 {@code eq}，非法值抛参数错误）
     *
     * @param op 原始操作符
     * @return 合法操作符
     * @throws BizException 操作符不在白名单时抛出 {@code PARAM_ERROR}
     */
    private static String normalizeOp(String op) {
        String raw = StringUtils.hasText(op) ? op.trim() : OP_EQ;
        String normalized = normalizeAlias(raw);
        for (String candidate : FILTER_OPS) {
            if (candidate.equalsIgnoreCase(normalized)) {
                return candidate;
            }
        }
        throw paramError("不支持的条件操作符：" + op);
    }

    /**
     * 归一化联动规则的空值操作符别名（empty → isNull、notEmpty → notNull）
     *
     * @param op 原始操作符
     * @return 归一化后的操作符
     */
    private static String normalizeAlias(String op) {
        if ("empty".equalsIgnoreCase(op)) {
            return "isNull";
        }
        if ("notEmpty".equalsIgnoreCase(op)) {
            return "notNull";
        }
        return op;
    }

    /**
     * 校验操作符与字段类型匹配（isNull / notNull 对所有可过滤类型开放）
     *
     * @param field 字段定义
     * @param op 已归一化的操作符
     * @throws BizException 类型不支持该操作符时抛出 {@code PARAM_ERROR}
     */
    private static void assertOpAllowed(FormField field, String op) {
        if ("isNull".equals(op) || "notNull".equals(op)) {
            return;
        }
        Set<String> allowed;
        switch (field.getType()) {
            case FormDataType.NUMBER:
            case FormDataType.DATE:
            case FormDataType.DATETIME:
                allowed = COMPARABLE_OPS;
                break;
            case FormDataType.BOOL:
                allowed = BOOL_OPS;
                break;
            default:
                allowed = TEXT_OPS;
                break;
        }
        if (!allowed.contains(op)) {
            throw paramError("字段 " + labelOf(field) + " 不支持操作符 " + op);
        }
    }

    /**
     * 取标量值（数组/对象/空值均视为非法）
     *
     * @param field 字段定义
     * @param value 条件值
     * @return 标量文本
     * @throws BizException 取值为空或非标量时抛出 {@code PARAM_ERROR}
     */
    private static String scalarValue(FormField field, Object value) {
        if (value == null || value instanceof Collection<?> || value instanceof Map<?, ?>) {
            throw paramError("字段 " + labelOf(field) + " 的条件值非法");
        }
        return String.valueOf(value);
    }

    /**
     * 取数组值（in / between 使用）
     *
     * @param field 字段定义
     * @param value 条件值
     * @return 值列表
     * @throws BizException 取值不是数组时抛出 {@code PARAM_ERROR}
     */
    private static List<Object> listValue(FormField field, Object value) {
        if (!(value instanceof Collection<?> collection)) {
            throw paramError("字段 " + labelOf(field) + " 的 in/between 条件值必须是数组");
        }
        return new ArrayList<>(collection);
    }

    /**
     * 按字段存储类型转换条件值（数值 → BigDecimal、布尔 → 0/1、日期 → 校验后文本）
     *
     * @param field 字段定义
     * @param value 原始条件值
     * @return JDBC 可绑定值
     * @throws BizException 值格式非法时抛出 {@code PARAM_ERROR}
     */
    private static Object coerce(FormField field, Object value) {
        String type = field.getType();
        if (FormDataType.NUMBER.equals(type)) {
            return coerceNumber(field, value);
        }
        if (FormDataType.BOOL.equals(type)) {
            return coerceBool(field, value);
        }
        if (FormDataType.DATE.equals(type) || FormDataType.DATETIME.equals(type)) {
            return coerceDate(field, value);
        }
        return scalarValue(field, value);
    }

    /**
     * 数值条件值转换（数值透传，其余按文本解析为 {@link BigDecimal}）
     *
     * @param field 字段定义
     * @param value 原始条件值
     * @return 数值
     * @throws BizException 非数值时抛出 {@code PARAM_ERROR}
     */
    private static Object coerceNumber(FormField field, Object value) {
        if (value instanceof Number number) {
            return number;
        }
        try {
            return new BigDecimal(scalarValue(field, value).trim());
        } catch (NumberFormatException e) {
            throw paramError("字段 " + labelOf(field) + " 的数值格式非法：" + value);
        }
    }

    /**
     * 布尔条件值转换（true/1 → 1，false/0 → 0）
     *
     * @param field 字段定义
     * @param value 原始条件值
     * @return 0 或 1
     * @throws BizException 无法识别布尔值时抛出 {@code PARAM_ERROR}
     */
    private static Object coerceBool(FormField field, Object value) {
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (value instanceof Number number) {
            return number.intValue() == 0 ? 0 : 1;
        }
        String text = scalarValue(field, value).trim().toLowerCase();
        if ("true".equals(text) || "1".equals(text)) {
            return 1;
        }
        if ("false".equals(text) || "0".equals(text)) {
            return 0;
        }
        throw paramError("字段 " + labelOf(field) + " 的布尔值非法：" + value);
    }

    /**
     * 日期/日期时间条件值校验（仅接受 {@code yyyy-MM-dd} 或 {@code yyyy-MM-dd HH:mm[:ss]}，并校验真实日历值）
     *
     * @param field 字段定义
     * @param value 原始条件值
     * @return 校验后的文本
     * @throws BizException 格式非法或日期不存在时抛出 {@code PARAM_ERROR}
     */
    private static Object coerceDate(FormField field, Object value) {
        String text = scalarValue(field, value).trim();
        Pattern pattern = FormDataType.DATE.equals(field.getType()) ? DATE_PATTERN : DATETIME_PATTERN;
        if (!pattern.matcher(text).matches()) {
            throw paramError("字段 " + labelOf(field) + " 的日期值格式非法：" + text);
        }
        try {
            if (FormDataType.DATE.equals(field.getType())) {
                LocalDate.parse(text);
            } else {
                String normalized = text.replace(' ', 'T');
                if (normalized.length() == 16) {
                    normalized = normalized + ":00";
                }
                LocalDateTime.parse(normalized);
            }
        } catch (DateTimeParseException e) {
            throw paramError("字段 " + labelOf(field) + " 的日期值不存在：" + text);
        }
        return text;
    }

    /**
     * 转义 LIKE 通配符（以 {@code !} 为转义符，SQL 显式 {@code ESCAPE '!'}，不依赖 sql_mode 的反斜杠语义）
     *
     * @param text 原始文本
     * @return 转义后的文本
     */
    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /**
     * 操作符对应的 SQL 比较符
     *
     * @param op 已归一化的操作符
     * @return SQL 比较符
     */
    private static String sqlOperator(String op) {
        switch (op) {
            case "ne":
                return "<>";
            case "gt":
                return ">";
            case "gte":
                return ">=";
            case "lt":
                return "<";
            case "lte":
                return "<=";
            default:
                return "=";
        }
    }

    /**
     * 字段显示名（缺省回落 key）
     *
     * @param field 字段定义
     * @return 显示名
     */
    private static String labelOf(FormField field) {
        return StringUtils.hasText(field.getLabel()) ? field.getLabel() : field.getKey();
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

    /**
     * 二次校验物理表名白名单（来源为服务端生成，防御性校验）
     *
     * @param table 物理表名
     * @return 校验通过的表名
     * @throws BizException 表名不匹配白名单时抛出 {@code PARAM_ERROR}
     */
    private static String assertTable(String table) {
        if (table == null || !TABLE_PATTERN.matcher(table).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "物理表名非法：" + table);
        }
        return table;
    }

    /**
     * 将列名列表转为反引号包裹的逗号串
     *
     * @param columns 列名列表
     * @return 反引号包裹的列名串
     */
    private static String quoteColumns(List<String> columns) {
        return columns.stream().map(column -> "`" + column + "`").collect(Collectors.joining(", "));
    }

    /**
     * 生成 {@code `col` = ?, ...} 形式的赋值串
     *
     * @param columns 列名列表
     * @return 赋值串
     */
    private static String assignments(List<String> columns) {
        return columns.stream().map(column -> "`" + column + "` = ?").collect(Collectors.joining(", "));
    }

    /**
     * 生成指定数量的 JDBC 占位符串
     *
     * @param count 占位符数量
     * @return 形如 {@code ?, ?, ?} 的占位符串
     */
    private static String placeholders(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append('?');
        }
        return builder.toString();
    }
}
