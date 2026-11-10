package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 表单结构定义（{@code schema_json}）解析器。
 *
 * <p>解析顶层 {@code fields} 数组为 {@link FormField} 列表，并对字段 key 的正则、保留列冲突、
 * 重复性以及 type 白名单做严格校验；任何违规抛出 {@code PARAM_ERROR}。
 * 解析结果同时是生成物理表 DDL 的列白名单来源。</p>
 *
 * @author BlueWhale
 */
public final class FormSchemaParser {

    /** 顶层字段数组的 JSON 键名 */
    public static final String KEY_FIELDS = "fields";

    /** 字段 key 允许的正则（小写字母开头，仅小写字母/数字/下划线，总长 ≤64） */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    /** 基础字段类型（子表子字段仅允许这些类型，禁止嵌套 attachment / subtable） */
    private static final Set<String> BASIC_TYPES = Set.of(
            FormDataType.TEXT, FormDataType.TEXTAREA, FormDataType.NUMBER, FormDataType.DATE,
            FormDataType.DATETIME, FormDataType.SELECT, FormDataType.BOOL);

    /** 字段类型白名单：基础类型 + 附件 + 子表 */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            FormDataType.TEXT, FormDataType.TEXTAREA, FormDataType.NUMBER, FormDataType.DATE,
            FormDataType.DATETIME, FormDataType.SELECT, FormDataType.BOOL,
            FormDataType.ATTACHMENT, FormDataType.SUBTABLE);

    /** 物理表保留列（动态字段 key 不得命中） */
    private static final Set<String> RESERVED_KEYS = Set.of(
            "id", "tenant_id", "definition_id", "definition_version", "biz_key",
            "submitter", "status", "create_time", "update_time", "deleted");

    /** text 字段默认长度 */
    static final int DEFAULT_TEXT_LENGTH = 255;

    /** text 字段最大长度 */
    static final int MAX_TEXT_LENGTH = 2000;

    /** JSON 解析器（仅读取，线程安全） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 工具类不允许实例化 */
    private FormSchemaParser() {
    }

    /**
     * 解析表单结构定义为字段列表
     *
     * @param schemaJson 表单结构定义 JSON
     * @return 按定义顺序排列的字段列表（非空）
     * @throws BizException 定义为空、非法 JSON、缺 {@code fields} 或字段校验不通过时抛出 {@code PARAM_ERROR}
     */
    public static List<FormField> parse(String schemaJson) {
        if (!StringUtils.hasText(schemaJson)) {
            throw paramError("表单结构定义为空，无法解析");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(schemaJson);
        } catch (Exception e) {
            throw paramError("表单结构定义不是合法 JSON");
        }
        JsonNode fieldsNode = root.get(KEY_FIELDS);
        if (fieldsNode == null || !fieldsNode.isArray() || fieldsNode.isEmpty()) {
            throw paramError("表单结构定义缺少非空的 fields 数组");
        }
        List<FormField> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (JsonNode node : fieldsNode) {
            result.add(parseField(node, keys));
        }
        return result;
    }

    /**
     * 断言列编码（物理列名）合法：匹配 key 正则且不命中保留列
     *
     * @param columnCode 列编码
     * @throws BizException 编码非法或与保留列冲突时抛出 {@code PARAM_ERROR}
     */
    public static void assertColumnCode(String columnCode) {
        if (!StringUtils.hasText(columnCode) || !KEY_PATTERN.matcher(columnCode).matches()) {
            throw paramError("列编码非法（需匹配 ^[a-z][a-z0-9_]{0,63}$）：" + columnCode);
        }
        if (RESERVED_KEYS.contains(columnCode)) {
            throw paramError("列编码与保留列冲突：" + columnCode);
        }
    }

    /**
     * 解析单个字段节点并做白名单校验
     *
     * @param node 字段 JSON 节点
     * @param keys 已出现的字段 key 集合（用于重复性校验）
     * @return 字段定义值对象
     * @throws BizException 字段校验不通过时抛出 {@code PARAM_ERROR}
     */
    private static FormField parseField(JsonNode node, Set<String> keys) {
        String key = text(node, "key");
        if (!StringUtils.hasText(key) || !KEY_PATTERN.matcher(key).matches()) {
            throw paramError("字段 key 非法（需匹配 ^[a-z][a-z0-9_]{0,63}$）：" + key);
        }
        if (RESERVED_KEYS.contains(key)) {
            throw paramError("字段 key 与保留列冲突：" + key);
        }
        if (!keys.add(key)) {
            throw paramError("字段 key 重复：" + key);
        }
        String type = text(node, "type");
        if (!StringUtils.hasText(type) || !ALLOWED_TYPES.contains(type)) {
            throw paramError("字段 type 非法：" + type);
        }
        FormField field = new FormField();
        String label = text(node, "label");
        field.setKey(key);
        field.setLabel(StringUtils.hasText(label) ? label : key);
        field.setType(type);
        field.setRequired(node.path("required").asBoolean(false));
        if (FormDataType.TEXT.equals(type)) {
            field.setMaxLength(parseMaxLength(node, key));
        }
        if (FormDataType.ATTACHMENT.equals(type)) {
            parseAttachment(node, key, field);
        }
        if (FormDataType.SUBTABLE.equals(type)) {
            parseSubtable(node, key, field);
        }
        field.setOptions(readOptions(node));
        return field;
    }

    /**
     * 解析附件字段配置（multiple / accept / maxCount）
     *
     * @param node 字段 JSON 节点
     * @param key 字段 key（错误提示）
     * @param field 目标字段
     * @throws BizException 配置非法时抛出 {@code PARAM_ERROR}
     */
    private static void parseAttachment(JsonNode node, String key, FormField field) {
        int maxCount = node.path("maxCount").asInt(0);
        if (maxCount < 0) {
            throw paramError("attachment 字段 maxCount 不能为负数：" + key);
        }
        field.setMultiple(node.path("multiple").asBoolean(true));
        field.setAccept(text(node, "accept"));
        field.setMaxCount(maxCount);
    }

    /**
     * 解析子表字段配置（children / minRows / maxRows）
     *
     * @param node 字段 JSON 节点
     * @param key 字段 key（错误提示）
     * @param field 目标字段
     * @throws BizException children 缺失、为空、子字段非法或行数配置非法时抛出 {@code PARAM_ERROR}
     */
    private static void parseSubtable(JsonNode node, String key, FormField field) {
        JsonNode childrenNode = node.get("children");
        if (childrenNode == null || !childrenNode.isArray() || childrenNode.isEmpty()) {
            throw paramError("subtable 字段必须配置非空的 children：" + key);
        }
        List<FormField> children = new ArrayList<>();
        Set<String> childKeys = new HashSet<>();
        for (JsonNode childNode : childrenNode) {
            children.add(parseChild(childNode, childKeys));
        }
        int minRows = node.path("minRows").asInt(0);
        int maxRows = node.path("maxRows").asInt(0);
        if (minRows < 0 || maxRows < 0) {
            throw paramError("subtable 字段 minRows/maxRows 不能为负数：" + key);
        }
        if (maxRows > 0 && minRows > maxRows) {
            throw paramError("subtable 字段 minRows 不能大于 maxRows：" + key);
        }
        field.setChildren(children);
        field.setMinRows(minRows);
        field.setMaxRows(maxRows);
    }

    /**
     * 解析子表子字段（仅允许基础类型，禁止嵌套子表/附件）
     *
     * @param node 子字段 JSON 节点
     * @param keys 已出现的子字段 key 集合（用于重复性校验）
     * @return 子字段定义
     * @throws BizException 子字段非法时抛出 {@code PARAM_ERROR}
     */
    private static FormField parseChild(JsonNode node, Set<String> keys) {
        String key = text(node, "key");
        if (!StringUtils.hasText(key) || !KEY_PATTERN.matcher(key).matches()) {
            throw paramError("子字段 key 非法（需匹配 ^[a-z][a-z0-9_]{0,63}$）：" + key);
        }
        if (!keys.add(key)) {
            throw paramError("子字段 key 重复：" + key);
        }
        String type = text(node, "type");
        if (FormDataType.SUBTABLE.equals(type)) {
            throw paramError("子表字段不允许嵌套子表：" + key);
        }
        if (!StringUtils.hasText(type) || !BASIC_TYPES.contains(type)) {
            throw paramError("子字段 type 非法（仅允许基础类型）：" + key + " -> " + type);
        }
        FormField child = new FormField();
        String label = text(node, "label");
        child.setKey(key);
        child.setLabel(StringUtils.hasText(label) ? label : key);
        child.setType(type);
        child.setRequired(node.path("required").asBoolean(false));
        if (FormDataType.TEXT.equals(type)) {
            child.setMaxLength(parseMaxLength(node, key));
        }
        child.setOptions(readOptions(node));
        return child;
    }

    /**
     * 解析 text 字段最大长度
     *
     * @param node 字段 JSON 节点
     * @param key 字段 key（错误提示）
     * @return 最大长度
     * @throws BizException 越界时抛出 {@code PARAM_ERROR}
     */
    private static Integer parseMaxLength(JsonNode node, String key) {
        int value = node.path("maxLength").asInt(DEFAULT_TEXT_LENGTH);
        if (value < 1 || value > MAX_TEXT_LENGTH) {
            throw paramError("text 字段 maxLength 需在 1..2000：" + key);
        }
        return value;
    }

    /**
     * 读取选项值数组（可选）
     *
     * @param node 字段 JSON 节点
     * @return 选项值列表；未配置时返回 {@code null}
     */
    private static List<String> readOptions(JsonNode node) {
        JsonNode optionsNode = node.get("options");
        if (optionsNode == null || !optionsNode.isArray() || optionsNode.isEmpty()) {
            return null;
        }
        List<String> options = new ArrayList<>();
        for (JsonNode option : optionsNode) {
            options.add(option.asText());
        }
        return options;
    }

    /**
     * 读取节点上的文本属性（缺失或 null 返回 {@code null}）
     *
     * @param node 字段 JSON 节点
     * @param name 属性名
     * @return 属性文本值；缺失时返回 {@code null}
     */
    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asText();
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
