package com.bluewhale.form.support.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.form.entity.FormData;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormDataType;
import com.bluewhale.form.support.FormField;
import com.bluewhale.form.support.FormTableDdlGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public void insert(String table, FormData record, List<FormField> fields, Map<String, Object> data) {
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
    public void update(String table, FormData record, List<FormField> fields, Map<String, Object> data) {
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
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "附件/子表数据序列化失败");
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
            return MAPPER.readValue(text, new TypeReference<List<Object>>() {
            });
        } catch (Exception e) {
            return value;
        }
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
