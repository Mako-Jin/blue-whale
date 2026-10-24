package com.bluewhale.im.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 智能体轨迹脱敏：在工具轨迹与引用落库前遮蔽敏感键值与密钥赋值片段。
 *
 * <p>键名命中 {@code password/token/secret} 等时整值替换为 {@code ***}；
 * 字符串内部形如 {@code password=xxx} 的片段仅遮蔽值。保留键名与结构，便于前端折叠展示。</p>
 *
 * @author BlueWhale
 */
public final class AgentTraceMasker {

    /** 整值遮蔽的敏感键名（小写） */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "secret", "token", "accesskey", "access_key",
            "apikey", "api_key", "authorization", "cookie");

    /** 敏感键值对（仅遮蔽值） */
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(password|passwd|secret|token|access[_-]?key|api[_-]?key)\\b\\s*[:=]\\s*[\"']?([^\"'\\s,;)}{]+)");

    /** 脱敏占位 */
    private static final String MASK = "***";

    /** 工具类不允许实例化 */
    private AgentTraceMasker() {
    }

    /**
     * 递归脱敏通用 JSON 对象（Map/List/String）。
     *
     * @param value 原始值
     * @return 脱敏后的副本；非容器/字符串类型原样返回
     */
    public static Object mask(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>(source.size());
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                String key = String.valueOf(entry.getKey());
                result.put(key, isSensitiveKey(key) ? MASK : mask(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> source) {
            List<Object> result = new ArrayList<>(source.size());
            for (Object item : source) {
                result.add(mask(item));
            }
            return result;
        }
        if (value instanceof String text) {
            return SECRET_ASSIGNMENT.matcher(text).replaceAll("$1=***");
        }
        return value;
    }

    /**
     * 判断键名是否属于敏感键。
     *
     * @param key 键名
     * @return 敏感返回 {@code true}
     */
    private static boolean isSensitiveKey(String key) {
        return SENSITIVE_KEYS.contains(key.trim().toLowerCase());
    }
}
