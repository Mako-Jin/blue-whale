package com.bluewhale.workflow.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程定义节点解析器：将 {@code definition_json} 解析为有序审批节点列表。
 *
 * <p>约定：{@code definition_json} 形如
 * {@code {"nodes":[{"key":"n1","name":"一级审批","assignee":"admin"}, ...]}}，
 * {@code nodes} 为有序数组，下标即审批推进顺序；每个节点取 {@code name}（可空）与
 * {@code assignee}（必填）。解析失败、{@code nodes} 缺失/为空、任一 {@code assignee} 为空时，
 * 抛出 {@code PARAM_ERROR}「流程定义缺少有效审批节点」。</p>
 *
 * @author BlueWhale
 */
public final class ProcessNodeParser {

    /** JSON 解析器（线程安全，可复用） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 工具类不允许实例化 */
    private ProcessNodeParser() {
    }

    /**
     * 解析流程定义 JSON 为有序节点列表。
     *
     * @param definitionJson 流程定义 JSON 文本
     * @return 有序审批节点列表（非空）
     */
    public static List<Node> parse(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            throw invalid();
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(definitionJson);
        } catch (Exception e) {
            throw invalid();
        }
        JsonNode nodesNode = root == null ? null : root.get("nodes");
        if (nodesNode == null || !nodesNode.isArray() || nodesNode.isEmpty()) {
            throw invalid();
        }
        List<Node> nodes = new ArrayList<>();
        for (JsonNode item : nodesNode) {
            String assignee = text(item, "assignee");
            if (assignee == null || assignee.isBlank()) {
                throw invalid();
            }
            Node node = new Node();
            node.setKey(text(item, "key"));
            node.setName(text(item, "name"));
            node.setAssignee(assignee);
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 读取节点文本字段，缺失或为 null 返回 {@code null}。
     *
     * @param item 节点 JSON
     * @param field 字段名
     * @return 字段文本值，缺失返回 {@code null}
     */
    private static String text(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 构造「流程定义缺少有效审批节点」业务异常。
     *
     * @return 业务异常
     */
    private static BizException invalid() {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程定义缺少有效审批节点");
    }

    /**
     * 审批节点。
     *
     * @author BlueWhale
     */
    @Data
    public static class Node {

        /** 节点键 */
        private String key;

        /** 节点名称 */
        private String name;

        /** 审批人账号 */
        private String assignee;
    }
}
