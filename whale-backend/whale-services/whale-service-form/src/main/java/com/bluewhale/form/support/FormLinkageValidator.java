package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 表单字段联动规则校验器。
 *
 * <p>联动规则以 {@code validate_json} 中的 {@code linkage} 节点持久化，不新增数据库列。
 * 结构形如 {@code {"action":"show|hide|require|disable","logic":"and|or",
 * "conditions":[{"field":"other_key","operator":"eq|ne|in|notIn|gt|lt|empty|notEmpty","value":"..."}]}}。</p>
 *
 * <p>空值操作符同时接受 {@code empty/notEmpty} 与过滤查询侧的 {@code isNull/notNull} 两套命名，
 * 避免前后端对同一语义使用不同操作符。</p>
 *
 * @author BlueWhale
 */
public final class FormLinkageValidator {

    /** 允许的联动动作 */
    private static final Set<String> ACTIONS = Set.of("show", "hide", "require", "disable");

    /** 允许的条件逻辑 */
    private static final Set<String> LOGICS = Set.of("and", "or");

    /** 允许的条件操作符（isNull/notNull 为 empty/notEmpty 的等价别名） */
    private static final Set<String> OPERATORS =
            Set.of("eq", "ne", "in", "notIn", "gt", "lt", "empty", "notEmpty", "isNull", "notNull");

    /** 条件对象允许的键 */
    private static final Set<String> CONDITION_KEYS = Set.of("field", "operator", "value");

    /** 被引用字段 key 的格式 */
    private static final Pattern FIELD_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    /** 单个联动的最大条件数 */
    private static final int MAX_CONDITIONS = 5;

    /** 工具类不允许实例化 */
    private FormLinkageValidator() {
    }

    /**
     * 校验联动规则
     *
     * @param linkage 联动规则节点
     * @param currentKey 当前字段 key（禁止自引用）
     * @param allKeys 本表单全部数据字段 key 集合
     * @param label 当前字段显示名（错误提示）
     * @throws BizException 联动规则非法时抛出 {@code PARAM_ERROR}
     */
    public static void validate(JsonNode linkage, String currentKey, Set<String> allKeys, String label) {
        if (linkage == null || !linkage.isObject()) {
            throw paramError("字段 " + label + " 的联动规则必须为 JSON 对象");
        }
        validateAction(linkage.get("action"), label);
        validateLogic(linkage.get("logic"), label);
        JsonNode conditions = linkage.get("conditions");
        if (conditions == null || !conditions.isArray() || conditions.isEmpty()) {
            throw paramError("字段 " + label + " 的联动条件必须为非空数组");
        }
        if (conditions.size() > MAX_CONDITIONS) {
            throw paramError("字段 " + label + " 的联动条件不能超过 " + MAX_CONDITIONS + " 个");
        }
        for (JsonNode condition : conditions) {
            validateCondition(condition, currentKey, allKeys, label);
        }
    }

    /**
     * 校验联动动作
     *
     * @param action 动作节点（可空）
     * @param label 当前字段显示名
     */
    private static void validateAction(JsonNode action, String label) {
        if (action == null || !action.isTextual() || !ACTIONS.contains(action.asText())) {
            throw paramError("字段 " + label + " 的联动动作非法");
        }
    }

    /**
     * 校验条件逻辑（缺省 {@code and}）
     *
     * @param logic 逻辑节点（可空）
     * @param label 当前字段显示名
     */
    private static void validateLogic(JsonNode logic, String label) {
        if (logic == null || logic.isNull()) {
            return;
        }
        if (!logic.isTextual() || !LOGICS.contains(logic.asText())) {
            throw paramError("字段 " + label + " 的联动逻辑非法");
        }
    }

    /**
     * 校验单个联动条件
     *
     * @param condition 条件节点
     * @param currentKey 当前字段 key
     * @param allKeys 本表单全部数据字段 key 集合
     * @param label 当前字段显示名
     */
    private static void validateCondition(JsonNode condition, String currentKey, Set<String> allKeys,
                                          String label) {
        if (condition == null || !condition.isObject()) {
            throw paramError("字段 " + label + " 的联动条件必须为 JSON 对象");
        }
        assertKnownKeys(condition, label);
        String fieldKey = validateConditionField(condition.get("field"), currentKey, allKeys, label);
        String operator = validateOperator(condition.get("operator"), label);
        if ("in".equals(operator) || "notIn".equals(operator)) {
            validateInValue(condition.get("value"), fieldKey, label);
        }
    }

    /**
     * 校验条件对象不含未知键
     *
     * @param condition 条件节点
     * @param label 当前字段显示名
     */
    private static void assertKnownKeys(JsonNode condition, String label) {
        Iterator<String> names = condition.fieldNames();
        while (names.hasNext()) {
            if (!CONDITION_KEYS.contains(names.next())) {
                throw paramError("字段 " + label + " 的联动条件存在未知键");
            }
        }
    }

    /**
     * 校验条件引用的字段
     *
     * @param field 字段节点
     * @param currentKey 当前字段 key
     * @param allKeys 本表单全部数据字段 key 集合
     * @param label 当前字段显示名
     * @return 被引用字段 key
     */
    private static String validateConditionField(JsonNode field, String currentKey, Set<String> allKeys,
                                                 String label) {
        if (field == null || !field.isTextual() || !FIELD_PATTERN.matcher(field.asText()).matches()) {
            throw paramError("字段 " + label + " 的联动条件字段非法");
        }
        String fieldKey = field.asText();
        if (fieldKey.equals(currentKey)) {
            throw paramError("字段 " + label + " 的联动条件不能引用自身");
        }
        if (!allKeys.contains(fieldKey)) {
            throw paramError("字段 " + label + " 的联动条件引用了不存在的字段：" + fieldKey);
        }
        return fieldKey;
    }

    /**
     * 校验条件操作符
     *
     * @param operator 操作符节点
     * @param label 当前字段显示名
     * @return 操作符文本
     */
    private static String validateOperator(JsonNode operator, String label) {
        if (operator == null || !operator.isTextual() || !OPERATORS.contains(operator.asText())) {
            throw paramError("字段 " + label + " 的联动条件操作符非法");
        }
        return operator.asText();
    }

    /**
     * 校验 {@code in/notIn} 的取值：非空逗号分隔字符串或非空数组
     *
     * @param value 取值节点
     * @param fieldKey 被引用字段 key（错误提示）
     * @param label 当前字段显示名
     */
    private static void validateInValue(JsonNode value, String fieldKey, String label) {
        if (value == null || value.isNull()) {
            throw paramError("字段 " + label + " 的联动条件取值不能为空：" + fieldKey);
        }
        if (value.isTextual()) {
            validateTextValue(value.asText(), fieldKey, label);
            return;
        }
        if (value.isArray()) {
            if (value.isEmpty()) {
                throw paramError("字段 " + label + " 的联动条件取值不能为空：" + fieldKey);
            }
            for (JsonNode item : value) {
                if (item == null || item.isNull() || !StringUtils.hasText(item.asText())) {
                    throw paramError("字段 " + label + " 的联动条件取值不能为空：" + fieldKey);
                }
            }
            return;
        }
        throw paramError("字段 " + label + " 的联动条件取值必须为非空字符串或数组：" + fieldKey);
    }

    /**
     * 校验逗号分隔字符串取值每一段均非空
     *
     * @param text 取值文本
     * @param fieldKey 被引用字段 key
     * @param label 当前字段显示名
     */
    private static void validateTextValue(String text, String fieldKey, String label) {
        if (!StringUtils.hasText(text)) {
            throw paramError("字段 " + label + " 的联动条件取值不能为空：" + fieldKey);
        }
        for (String part : text.split(",", -1)) {
            if (!StringUtils.hasText(part)) {
                throw paramError("字段 " + label + " 的联动条件取值不能为空：" + fieldKey);
            }
        }
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
