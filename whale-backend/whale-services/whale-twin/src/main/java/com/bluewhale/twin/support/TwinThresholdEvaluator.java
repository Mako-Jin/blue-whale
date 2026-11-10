package com.bluewhale.twin.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 绑定阈值评估器（纯函数）：按 {@code propertyMap} 里的 {@code thresholds}
 * 对实时态取值分级，供运行时差分告警。
 *
 * <p>分级口径与前端配色一致：{@code thresholds: [t1, t2]} 时
 * {@code v >= t2} 为 critical（红）、{@code v >= t1} 为 warn（橙）、
 * 其余为 normal（绿）；只给单个阈值时 {@code v >= t} 为 warn。
 * 仅数值参与比较（Number 或可解析的数值字符串），非数值/缺阈值的键不产生级别。</p>
 *
 * <p>6.07 起每个状态键可追加告警规则：{@code confirmSeconds}（升格持续确认秒数）
 * 与 {@code recoveryDelta}（降格回差），由 {@link TwinAlertTracker} 消费。</p>
 *
 * @author BlueWhale
 */
public final class TwinThresholdEvaluator {

    /** 级别：正常（绿） */
    public static final String LEVEL_NORMAL = "normal";

    /** 级别：警告（橙） */
    public static final String LEVEL_WARN = "warn";

    /** 级别：严重（红） */
    public static final String LEVEL_CRITICAL = "critical";

    /** 持续确认秒数上限（防止误配导致告警永不触发） */
    static final int MAX_CONFIRM_SECONDS = 3600;

    /** JSON 解析器（读 propertyMap 的 stateKey 与 thresholds） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 工具类，不实例化。 */
    private TwinThresholdEvaluator() {
    }

    /**
     * 状态键的告警规则（缺省即旧行为：立即升格、无回差）。
     *
     * @param confirmSeconds 升格持续确认秒数（0 = 立即生效）
     * @param recoveryDelta  降格回差（0 = 无回差）
     */
    public record Rule(int confirmSeconds, BigDecimal recoveryDelta) {

        /** 无规则（立即升格 / 无回差） */
        public static final Rule DEFAULT = new Rule(0, BigDecimal.ZERO);
    }

    /**
     * 单个状态键的级别评估结果。
     *
     * @param stateKey   实体状态键
     * @param level      级别（normal / warn / critical）
     * @param value      参与评估的原始取值
     * @param thresholds 参与判定的阈值（升序）
     * @param rule       告警规则（缺省为 {@link Rule#DEFAULT}）
     */
    public record Level(String stateKey, String level, Object value, List<BigDecimal> thresholds,
                        Rule rule) {
    }

    /**
     * 评估实时态的阈值级别。
     *
     * @param propertyMap 属性映射 JSON（thresholds 定义在此）
     * @param values      状态键 → 最新取值
     * @return 状态键 → 评估结果（无阈值或非数值的键不在结果里）
     */
    public static Map<String, Level> evaluate(String propertyMap, Map<String, Object> values) {
        Map<String, Level> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(propertyMap) || values == null || values.isEmpty()) {
            return result;
        }
        try {
            JsonNode mapping = JSON.readTree(propertyMap);
            if (!mapping.isObject()) {
                return result;
            }
            var fields = mapping.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode target = entry.getValue();
                String stateKey = entry.getKey();
                if (target.isTextual()) {
                    stateKey = target.asText(stateKey);
                } else if (target.isObject() && target.hasNonNull("stateKey")) {
                    stateKey = target.get("stateKey").asText(stateKey);
                }
                List<BigDecimal> thresholds = parseThresholds(
                        target.isObject() ? target.get("thresholds") : null);
                if (thresholds.isEmpty()) {
                    continue;
                }
                Object raw = values.get(stateKey);
                BigDecimal number = toNumber(raw);
                if (number == null) {
                    continue;
                }
                result.put(stateKey, new Level(stateKey,
                        classify(number, thresholds), raw, thresholds,
                        parseRule(target)));
            }
        } catch (Exception e) {
            // 属性映射解析失败按无告警处理，不阻塞实时态主链路
            return Map.of();
        }
        return result;
    }

    /**
     * 解析状态键上的告警规则（非对象或缺字段时取缺省值）。
     *
     * @param target propertyMap 的状态键值节点
     * @return 规则（永不为 {@code null}）
     */
    private static Rule parseRule(JsonNode target) {
        if (target == null || !target.isObject()) {
            return Rule.DEFAULT;
        }
        int confirmSeconds = 0;
        JsonNode confirmNode = target.get("confirmSeconds");
        if (confirmNode != null && confirmNode.isNumber()) {
            confirmSeconds = Math.max(0, Math.min(confirmNode.asInt(0), MAX_CONFIRM_SECONDS));
        }
        BigDecimal recoveryDelta = BigDecimal.ZERO;
        JsonNode deltaNode = target.get("recoveryDelta");
        if (deltaNode != null && deltaNode.isNumber() && deltaNode.decimalValue().signum() > 0) {
            recoveryDelta = deltaNode.decimalValue();
        }
        return new Rule(confirmSeconds, recoveryDelta);
    }

    /**
     * 解析阈值数组（数值节点，最多取两个；缺失/非法返回空列表）。
     *
     * @param node thresholds 节点（可空）
     * @return 升序阈值列表
     */
    private static List<BigDecimal> parseThresholds(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        List<BigDecimal> thresholds = new ArrayList<>(2);
        for (JsonNode item : node) {
            BigDecimal number = toNumber(item.isNumber() ? item.decimalValue() : item.asText());
            if (number == null) {
                return List.of();
            }
            thresholds.add(number);
        }
        Collections.sort(thresholds);
        return List.copyOf(thresholds);
    }

    /**
     * 按阈值分级（升序阈值：最高为 critical 界，次高/唯一为 warn 界）。
     *
     * @param value      取值
     * @param thresholds 升序阈值
     * @return 级别常量
     */
    private static String classify(BigDecimal value, List<BigDecimal> thresholds) {
        if (thresholds.size() >= 2 && value.compareTo(thresholds.get(thresholds.size() - 1)) >= 0) {
            return LEVEL_CRITICAL;
        }
        if (value.compareTo(thresholds.get(0)) >= 0) {
            return LEVEL_WARN;
        }
        return LEVEL_NORMAL;
    }

    /**
     * 宽松转数值：Number 直转，字符串可解析才算数值（NaN/Infinity 视为非法）。
     *
     * @param raw 原始取值
     * @return 数值；无法解析返回 {@code null}
     */
    static BigDecimal toNumber(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            if (raw instanceof BigDecimal decimal) {
                return decimal;
            }
            if (raw instanceof Number || raw instanceof String) {
                // "NaN"/"Infinity" 等非法数值由 NumberFormatException 统一拦截
                return new BigDecimal(String.valueOf(raw).trim());
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }
}
