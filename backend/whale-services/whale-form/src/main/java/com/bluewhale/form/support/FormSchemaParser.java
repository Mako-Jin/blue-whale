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

    /** 字段类型白名单 */
    private static final Set<String> ALLOWED_TYPES =
            Set.of("text", "textarea", "number", "date", "datetime", "select", "bool");

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
        Integer maxLength = null;
        if ("text".equals(type)) {
            int value = node.path("maxLength").asInt(DEFAULT_TEXT_LENGTH);
            if (value < 1 || value > MAX_TEXT_LENGTH) {
                throw paramError("text 字段 maxLength 需在 1..2000：" + key);
            }
            maxLength = value;
        }
        String label = text(node, "label");
        return new FormField(key, StringUtils.hasText(label) ? label : key, type,
                node.path("required").asBoolean(false), maxLength);
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
