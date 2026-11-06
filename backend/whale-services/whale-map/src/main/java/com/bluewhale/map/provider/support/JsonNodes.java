package com.bluewhale.map.provider.support;

import com.bluewhale.map.common.coord.CoordType;
import com.bluewhale.map.common.coord.GeoPoint;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 取值助手。
 *
 * <p>供应商返回体字段命名不统一（腾讯同一个接口里既有 {@code address_components} 又有
 * {@code address_component}，高德则把数字全写成字符串）。统一在这里做「取不到就是 null」
 * 的容错，业务代码按正常值写，不必到处判 {@code isMissingNode}。</p>
 *
 * @author BlueWhale
 */
public final class JsonNodes {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private JsonNodes() {
    }

    /**
     * 逐级取子节点
     *
     * @param node  根节点
     * @param paths 字段路径
     * @return 子节点；任一层缺失返回 {@code null}
     */
    public static JsonNode child(JsonNode node, String... paths) {
        JsonNode current = node;
        for (String path : paths) {
            if (current == null) {
                return null;
            }
            current = current.get(path);
        }
        return current;
    }

    /**
     * 取文本字段
     *
     * @param node  节点
     * @param field 字段名
     * @return 文本；缺失或为空返回 {@code null}
     */
    public static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() || "[]".equals(text) ? null : text;
    }

    /**
     * 取数值字段（兼容字符串形式的数字）
     *
     * @param node  节点
     * @param field 字段名
     * @return 数值；缺失或不可解析返回 {@code null}
     */
    public static Double number(JsonNode node, String field) {
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 取数组字段
     *
     * @param node  节点
     * @param field 字段名
     * @return 元素列表；缺失或非数组返回空列表
     */
    public static List<JsonNode> items(JsonNode node, String field) {
        JsonNode array = node == null ? null : node.get(field);
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<JsonNode> items = new ArrayList<>(array.size());
        array.forEach(items::add);
        return items;
    }

    /**
     * 解析 {@code lng,lat} 形式的坐标串
     *
     * @param text 坐标串
     * @param type 坐标系
     * @return 坐标点；格式不合法返回 {@code null}
     */
    public static GeoPoint parseLngLat(String text, CoordType type) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new GeoPoint(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()), type);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
