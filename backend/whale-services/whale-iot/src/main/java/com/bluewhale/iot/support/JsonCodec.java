package com.bluewhale.iot.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON 编解码工具（Jackson 2 静态实例）。
 *
 * <p>Spring Boot 4 的自动配置只提供 Jackson 3 的 {@code JsonMapper} Bean，
 * 平台内统一用 Jackson 2 的静态 {@link ObjectMapper}，避免注入歧义（对齐 whale-assistant）。</p>
 *
 * @author BlueWhale
 */
public final class JsonCodec {

    /** 平台统一 Jackson 2 实例 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private JsonCodec() {
    }

    /**
     * 序列化为 JSON 字符串。
     *
     * @param value 任意对象
     * @return JSON 字符串
     */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    /**
     * 解析为 JSON 树。
     *
     * @param json JSON 字符串
     * @return JSON 树；空串或非法内容返回 {@code null}
     */
    public static JsonNode tree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 解析为键值对象（保持字段顺序）。
     *
     * @param json JSON 字符串
     * @return 键值映射；空串或非法内容返回空映射
     */
    public static Map<String, Object> readMap(String json) {
        JsonNode node = tree(json);
        if (node == null || !node.isObject()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), toValue(entry.getValue())));
        return result;
    }

    /**
     * 解析 JSON 值为 Java 值（数值/布尔/字符串/结构）。
     *
     * @param node JSON 节点
     * @return Java 值
     */
    public static Object toValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        try {
            return MAPPER.convertValue(node, Object.class);
        } catch (IllegalArgumentException e) {
            return node.toString();
        }
    }
}
