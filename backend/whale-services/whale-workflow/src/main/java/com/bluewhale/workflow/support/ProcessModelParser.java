package com.bluewhale.workflow.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 流程模型解析器：把 {@code definition_json} 解析为 {@link ProcessModel}。
 *
 * <p>模式判定（冻结契约 §3.1）：</p>
 * <ul>
 *   <li>{@code type=="stateMachine"} → 规范化模型；</li>
 *   <li>{@code type} 缺失且无 {@code flows} → <b>legacy</b>，原样委托
 *       {@link ProcessNodeParser}（其文案「流程定义缺少有效审批节点」逐字保留）；</li>
 *   <li>{@code type} 缺失但存在 {@code flows} → 规范化（宽容）；</li>
 *   <li>其它非空 {@code type} → {@code UNKNOWN_DEFINITION_TYPE}。</li>
 * </ul>
 *
 * @author BlueWhale
 */
public final class ProcessModelParser {

    /** 规范化定义类型标识 */
    public static final String TYPE_STATE_MACHINE = "stateMachine";

    /** 节点数上限 */
    public static final int MAX_NODES = 200;

    /** 流转数上限 */
    public static final int MAX_FLOWS = 400;

    /** 定义类型文本 */
    private static final String FIELD_TYPE = "type";

    /** 节点数组字段 */
    private static final String FIELD_NODES = "nodes";

    /** 流转数组字段 */
    private static final String FIELD_FLOWS = "flows";

    /** legacy 非法定义的固定文案（与 {@link ProcessNodeParser} 完全一致） */
    private static final String LEGACY_INVALID_MESSAGE = "流程定义缺少有效审批节点";

    /** 节点键合法字符集（字母/数字/下划线/连字符，1~64 字符） */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    /** JSON 解析器（Jackson 2，线程安全可复用，与既有 support 代码一致） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 工具类不允许实例化 */
    private ProcessModelParser() {
    }

    /**
     * 解析流程定义 JSON。
     *
     * @param definitionJson 流程定义 JSON 文本
     * @return 流程模型
     * @throws ProcessModelException 定义非法时抛出（legacy 分支沿用既有文案）
     */
    public static ProcessModel parse(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            return legacy(definitionJson);
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(definitionJson);
        } catch (Exception e) {
            return legacy(definitionJson);
        }
        if (root == null || !root.isObject()) {
            return legacy(definitionJson);
        }
        String type = text(root, FIELD_TYPE);
        boolean hasFlows = root.has(FIELD_FLOWS) && root.get(FIELD_FLOWS).isArray();
        if (type != null && !type.isBlank() && !TYPE_STATE_MACHINE.equalsIgnoreCase(type.trim())) {
            throw new ProcessModelException(ValidationCode.UNKNOWN_DEFINITION_TYPE,
                    "流程定义非法：" + ValidationCode.UNKNOWN_DEFINITION_TYPE.name(), type);
        }
        if ((type == null || type.isBlank()) && !hasFlows) {
            return legacy(definitionJson);
        }
        return normalized(root, type);
    }

    /**
     * legacy 归一化：委托 {@link ProcessNodeParser}（字节级不变），再补齐顺序流转。
     *
     * @param definitionJson 旧格式 {@code {"nodes":[…]}} JSON 文本
     * @return 归一化后的流程模型（{@link ProcessModel#isLegacy()} = true）
     */
    private static ProcessModel legacy(String definitionJson) {
        List<ProcessNodeParser.Node> parsed = ProcessNodeParser.parse(definitionJson);
        if (parsed.size() > MAX_NODES) {
            throw tooManyNodes(parsed.size());
        }
        ProcessModel model = new ProcessModel();
        model.setLegacy(true);
        for (int i = 0; i < parsed.size(); i++) {
            ProcessNodeParser.Node source = parsed.get(i);
            ProcessModelNode node = new ProcessModelNode();
            String key = source.getKey() == null || source.getKey().isBlank() ? "n" + (i + 1) : source.getKey();
            node.setKey(key);
            node.setType(ProcessNodeType.USER_TASK);
            node.setName(source.getName());
            node.setAssignee(source.getAssignee());
            model.getNodes().add(node);
        }
        for (int i = 0; i + 1 < model.getNodes().size(); i++) {
            ProcessModelFlow flow = new ProcessModelFlow();
            flow.setKey("f" + (i + 1));
            flow.setFrom(model.getNodes().get(i).getKey());
            flow.setTo(model.getNodes().get(i + 1).getKey());
            model.getFlows().add(flow);
        }
        model.reindex();
        return model;
    }

    /**
     * 解析规范化定义。
     *
     * @param root 定义 JSON 根节点
     * @param type 定义类型文本（{@code null} 表示宽容模式）
     * @return 规范化流程模型
     * @throws ProcessModelException 结构非法时抛出
     */
    private static ProcessModel normalized(JsonNode root, String type) {
        JsonNode nodesNode = root.get(FIELD_NODES);
        if (nodesNode == null || !nodesNode.isArray() || nodesNode.isEmpty()) {
            throw new ProcessModelException(ValidationCode.MISSING_NODES, LEGACY_INVALID_MESSAGE);
        }
        if (nodesNode.size() > MAX_NODES) {
            throw tooManyNodes(nodesNode.size());
        }
        ProcessModel model = new ProcessModel();
        model.setDefinitionType(type == null || type.isBlank() ? TYPE_STATE_MACHINE : type);
        for (JsonNode item : nodesNode) {
            model.getNodes().add(readNode(item));
        }
        JsonNode flowsNode = root.get(FIELD_FLOWS);
        if (flowsNode != null && flowsNode.isArray()) {
            if (flowsNode.size() > MAX_FLOWS) {
                throw tooManyFlows(flowsNode.size());
            }
            for (JsonNode item : flowsNode) {
                model.getFlows().add(readFlow(item));
            }
        }
        model.reindex();
        return model;
    }

    /**
     * 解析单个节点。
     *
     * @param item 节点 JSON
     * @return 流程模型节点
     * @throws ProcessModelException 节点结构非法时抛出
     */
    private static ProcessModelNode readNode(JsonNode item) {
        String key = text(item, "key");
        if (key == null || key.isBlank()) {
            throw new ProcessModelException(ValidationCode.NODE_KEY_MISSING,
                    "流程定义非法：" + ValidationCode.NODE_KEY_MISSING.name());
        }
        if (!KEY_PATTERN.matcher(key.trim()).matches()) {
            throw new ProcessModelException(ValidationCode.NODE_KEY_INVALID,
                    "流程定义非法：" + ValidationCode.NODE_KEY_INVALID.name(), key);
        }
        ProcessNodeType nodeType = ProcessNodeType.from(text(item, FIELD_TYPE));
        if (nodeType == null) {
            throw new ProcessModelException(ValidationCode.UNKNOWN_NODE_TYPE,
                    "流程定义非法：" + ValidationCode.UNKNOWN_NODE_TYPE.name(), key);
        }
        ProcessModelNode node = new ProcessModelNode();
        node.setKey(key.trim());
        node.setType(nodeType);
        node.setName(text(item, "name"));
        node.setAssignee(text(item, "assignee"));
        if (nodeType == ProcessNodeType.USER_TASK && (node.getAssignee() == null || node.getAssignee().isBlank())) {
            throw new ProcessModelException(ValidationCode.USER_TASK_ASSIGNEE_REQUIRED, LEGACY_INVALID_MESSAGE, key);
        }
        return node;
    }

    /**
     * 解析单个流转。
     *
     * @param item 流转 JSON
     * @return 流程模型流转
     */
    private static ProcessModelFlow readFlow(JsonNode item) {
        ProcessModelFlow flow = new ProcessModelFlow();
        flow.setKey(text(item, "key"));
        flow.setFrom(text(item, "from"));
        flow.setTo(text(item, "to"));
        flow.setCondition(text(item, "condition"));
        JsonNode isDefault = item.get("isDefault");
        flow.setIsDefault(isDefault != null && !isDefault.isNull() && isDefault.asBoolean(false));
        return flow;
    }

    /**
     * 读取文本字段，缺失或为 {@code null} 返回 {@code null}。
     *
     * @param item JSON 节点
     * @param field 字段名
     * @return 字段文本值，缺失返回 {@code null}
     */
    private static String text(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 构造节点数超限异常。
     *
     * @param actual 实际节点数
     * @return 流程模型异常
     */
    private static ProcessModelException tooManyNodes(int actual) {
        return new ProcessModelException(ValidationCode.TOO_MANY_NODES,
                "流程定义非法：节点数 " + actual + " 超出上限 " + MAX_NODES);
    }

    /**
     * 构造流转数超限异常。
     *
     * @param actual 实际流转数
     * @return 流程模型异常
     */
    private static ProcessModelException tooManyFlows(int actual) {
        return new ProcessModelException(ValidationCode.TOO_MANY_FLOWS,
                "流程定义非法：流转数 " + actual + " 超出上限 " + MAX_FLOWS);
    }
}
