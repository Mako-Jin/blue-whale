package com.bluewhale.workflow.support;

import com.bluewhale.workflow.support.machine.ConditionGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 流程定义校验器：产出 {@link ValidationReport}，<b>内容非法不抛异常</b>。
 *
 * <p>校验分层：</p>
 * <ol>
 *   <li>模式判定（legacy / 规范化 / 未知类型）；</li>
 *   <li>节点/流转结构校验（key、类型、assignee、重复、引用、自环、条数上限）；</li>
 *   <li>图级校验（开始/结束事件、网关出流与默认分支、悬空、不可达、成环）——
 *       仅在结构无错误时执行，legacy 模式跳过（其顺序语义天然合法）。</li>
 * </ol>
 *
 * @author BlueWhale
 */
public final class ProcessModelValidator {

    /** legacy 非法定义的固定文案（与 {@link ProcessNodeParser} 一致） */
    private static final String LEGACY_INVALID_MESSAGE = "流程定义缺少有效审批节点";

    /** 节点键合法字符集 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    /** 审批节点表单编码合法字符集（字母/数字/下划线/连字符，1~64 字符） */
    private static final Pattern FORM_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    /** 会签策略合法格式：ALL / ANY / RATIO:1~100 */
    private static final Pattern COUNTER_SIGN_POLICY_PATTERN = Pattern.compile("^(ALL|ANY|RATIO:([1-9][0-9]?|100))$");

    /** JSON 解析器（Jackson 2） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 工具类不允许实例化 */
    private ProcessModelValidator() {
    }

    /**
     * 校验流程定义 JSON（永不抛异常）。
     *
     * @param definitionJson 流程定义 JSON 文本，可为 {@code null}
     * @return 校验报告
     */
    public static ValidationReport validate(String definitionJson) {
        ValidationReport report = new ValidationReport();
        if (definitionJson == null || definitionJson.isBlank()) {
            report.setMode(ValidationReport.MODE_LEGACY);
            report.add(ValidationIssue.of(ValidationCode.MISSING_NODES, LEGACY_INVALID_MESSAGE, null));
            return report;
        }
        JsonNode root = readTree(report, definitionJson);
        if (root == null) {
            return report;
        }
        String type = text(root, "type");
        JsonNode flowsNode = root.get("flows");
        boolean hasFlows = flowsNode != null && flowsNode.isArray() && !flowsNode.isEmpty();
        if (isUnknownType(type)) {
            report.setMode(ValidationReport.MODE_NORMALIZED);
            report.add(ValidationIssue.of(ValidationCode.UNKNOWN_DEFINITION_TYPE, type));
            return report;
        }
        boolean normalized = (type != null && !type.isBlank()) || hasFlows;
        report.setMode(normalized ? ValidationReport.MODE_NORMALIZED : ValidationReport.MODE_LEGACY);
        JsonNode nodesNode = root.get("nodes");
        if (isMissingNodes(nodesNode)) {
            report.add(ValidationIssue.of(ValidationCode.MISSING_NODES, LEGACY_INVALID_MESSAGE, null));
            return report;
        }
        checkNodeLimit(report, nodesNode);
        if (normalized) {
            checkGroups(report, root);
        }
        Graph graph = new Graph();
        checkNodes(report, nodesNode, normalized, graph);
        checkFlows(report, flowsNode, graph);
        if (normalized && report.isValid()) {
            checkGraph(report, graph);
        }
        return report;
    }

    /**
     * 判定定义类型是否为未知类型（非空且非 {@code stateMachine}）。
     *
     * @param type 定义类型文本，可为 {@code null}
     * @return 未知类型返回 {@code true}
     */
    private static boolean isUnknownType(String type) {
        return type != null && !type.isBlank()
                && !ProcessModelParser.TYPE_STATE_MACHINE.equalsIgnoreCase(type.trim());
    }

    /**
     * 判定节点数组是否缺失或为空。
     *
     * @param nodesNode 节点 JSON 节点，可为 {@code null}
     * @return 缺失或非数组或空数组返回 {@code true}
     */
    private static boolean isMissingNodes(JsonNode nodesNode) {
        return nodesNode == null || !nodesNode.isArray() || nodesNode.isEmpty();
    }

    /**
     * 校验节点条数上限。
     *
     * @param report 校验报告
     * @param nodesNode 节点 JSON 数组
     */
    private static void checkNodeLimit(ValidationReport report, JsonNode nodesNode) {
        if (nodesNode.size() > ProcessModelParser.MAX_NODES) {
            report.add(ValidationIssue.of(ValidationCode.TOO_MANY_NODES,
                    "节点数 " + nodesNode.size() + " 超出上限 " + ProcessModelParser.MAX_NODES, null));
        }
    }

    /**
     * 校验分组数组：条数上限、key 合法性 / 重复、节点归属引用。
     *
     * <p>仅规范化模式调用；legacy 定义无分组语义。</p>
     *
     * @param report 校验报告
     * @param root 定义 JSON 根节点
     */
    private static void checkGroups(ValidationReport report, JsonNode root) {
        JsonNode groupsNode = root.get("groups");
        if (groupsNode == null || !groupsNode.isArray()) {
            return;
        }
        if (groupsNode.size() > ProcessModelParser.MAX_GROUPS) {
            report.add(ValidationIssue.of(ValidationCode.TOO_MANY_GROUPS,
                    "分组数 " + groupsNode.size() + " 超出上限 " + ProcessModelParser.MAX_GROUPS, null));
        }
        Set<String> groupKeys = new LinkedHashSet<>();
        for (JsonNode item : groupsNode) {
            String key = text(item, "key");
            if (key == null || key.isBlank() || !KEY_PATTERN.matcher(key.trim()).matches()) {
                report.add(ValidationIssue.of(ValidationCode.GROUP_KEY_INVALID,
                        key == null || key.isBlank() ? "分组缺少 key" : "分组 key 不合法：" + key,
                        key == null || key.isBlank() ? null : key));
                continue;
            }
            String trimmed = key.trim();
            if (!groupKeys.add(trimmed)) {
                report.add(ValidationIssue.of(ValidationCode.GROUP_KEY_DUPLICATE, trimmed));
            }
        }
        checkNodeGroups(report, root.get("nodes"), groupKeys);
    }

    /**
     * 校验节点归属：非空的 {@code groupKey} 必须命中已声明分组。
     *
     * @param report 校验报告
     * @param nodesNode 节点 JSON 数组，可为 {@code null}
     * @param groupKeys 已声明的分组键集合
     */
    private static void checkNodeGroups(ValidationReport report, JsonNode nodesNode, Set<String> groupKeys) {
        if (nodesNode == null || !nodesNode.isArray()) {
            return;
        }
        for (JsonNode item : nodesNode) {
            String groupKey = text(item, "groupKey");
            if (groupKey != null && !groupKey.isBlank() && !groupKeys.contains(groupKey.trim())) {
                report.add(ValidationIssue.of(ValidationCode.NODE_GROUP_UNKNOWN, text(item, "key")));
            }
        }
    }

    /**
     * 校验已构建的流程模型（供 BPMN 导入复用，模型键/引用已由解析器保证）。
     *
     * @param model 流程模型
     * @param mode 定义模式（{@link ValidationReport#MODE_BPMN}）
     * @return 校验报告
     */
    public static ValidationReport validateModel(ProcessModel model, String mode) {
        ValidationReport report = new ValidationReport();
        report.setMode(mode);
        if (model == null || model.getNodes().isEmpty()) {
            report.add(ValidationIssue.of(ValidationCode.MISSING_NODES, LEGACY_INVALID_MESSAGE, null));
            return report;
        }
        Graph graph = new Graph();
        for (ProcessModelNode node : model.getNodes()) {
            graph.getNodeKeys().add(node.getKey());
            graph.getTypes().put(node.getKey(), node.getType());
        }
        for (ProcessModelFlow flow : model.getFlows()) {
            addEdge(graph, flow);
            checkCondition(report, flow.getCondition(), flow.getKey());
        }
        checkModelGroups(report, model);
        checkGraph(report, graph);
        return report;
    }

    /**
     * 校验模型内分组归属（BPMN 泳道导入后复用）。
     *
     * @param report 校验报告
     * @param model 流程模型
     */
    private static void checkModelGroups(ValidationReport report, ProcessModel model) {
        Set<String> groupKeys = new LinkedHashSet<>();
        for (ProcessModelGroup group : model.getGroups()) {
            if (group.getKey() != null) {
                groupKeys.add(group.getKey());
            }
        }
        for (ProcessModelNode node : model.getNodes()) {
            if (node.getGroupKey() != null && !node.getGroupKey().isBlank()
                    && !groupKeys.contains(node.getGroupKey())) {
                report.add(ValidationIssue.of(ValidationCode.NODE_GROUP_UNKNOWN, node.getKey()));
            }
        }
    }

    /**
     * 读取 JSON 根节点，失败时写入 {@code MISSING_NODES} 问题。
     *
     * @param report 校验报告
     * @param definitionJson 定义 JSON 文本
     * @return 根节点；解析失败返回 {@code null}
     */
    private static JsonNode readTree(ValidationReport report, String definitionJson) {
        report.setMode(ValidationReport.MODE_LEGACY);
        try {
            JsonNode root = OBJECT_MAPPER.readTree(definitionJson);
            if (root == null || !root.isObject()) {
                report.add(ValidationIssue.of(ValidationCode.MISSING_NODES, "流程定义不是合法 JSON 对象", null));
                return null;
            }
            return root;
        } catch (Exception e) {
            report.add(ValidationIssue.of(ValidationCode.MISSING_NODES, "流程定义不是合法 JSON", null));
            return null;
        }
    }

    /**
     * 校验节点数组：key、类型、assignee、重复。
     *
     * @param report 校验报告
     * @param nodesNode 节点 JSON 数组
     * @param normalized 是否规范化模式（legacy 允许缺省 key）
     * @param graph 图结构累积器
     */
    private static void checkNodes(ValidationReport report, JsonNode nodesNode, boolean normalized, Graph graph) {
        int index = 0;
        for (JsonNode item : nodesNode) {
            index++;
            String rawKey = text(item, "key");
            String key = rawKey == null || rawKey.isBlank() ? (normalized ? null : "n" + index) : rawKey.trim();
            checkNodeKey(report, key, index);
            if (key != null && graph.getNodeKeys().contains(key)) {
                report.add(ValidationIssue.of(ValidationCode.NODE_KEY_DUPLICATE, key));
            }
            ProcessNodeType nodeType = normalized
                    ? ProcessNodeType.from(text(item, "type")) : ProcessNodeType.USER_TASK;
            if (normalized && nodeType == null) {
                report.add(ValidationIssue.of(ValidationCode.UNKNOWN_NODE_TYPE, key));
                continue;
            }
            if (key != null) {
                graph.getNodeKeys().add(key);
                graph.getTypes().put(key, nodeType);
            }
            String assignee = text(item, "assignee");
            if (nodeType == ProcessNodeType.USER_TASK && (assignee == null || assignee.isBlank())) {
                report.add(ValidationIssue.of(ValidationCode.USER_TASK_ASSIGNEE_REQUIRED, key));
            }
            checkCounterSign(report, item, nodeType, key);
            checkCcTask(report, item, nodeType, key);
            checkUserTaskForm(report, item, nodeType, key);
        }
    }

    /**
     * 校验抄送节点：收件人（{@code assignees}）非空。
     *
     * @param report 校验报告
     * @param item 节点 JSON
     * @param nodeType 节点类型
     * @param key 节点键（可为 {@code null}）
     */
    private static void checkCcTask(ValidationReport report, JsonNode item, ProcessNodeType nodeType, String key) {
        if (nodeType != ProcessNodeType.CC_TASK) {
            return;
        }
        JsonNode assignees = item.get("assignees");
        boolean hasAssignees = assignees != null && ((assignees.isArray() && !assignees.isEmpty())
                || (!assignees.isArray() && !assignees.asText().isBlank()));
        if (!hasAssignees) {
            report.add(ValidationIssue.of(ValidationCode.CC_TASK_RECIPIENTS_REQUIRED, key));
        }
    }

    /**
     * 校验会签节点：assignees 非空且策略合法。
     *
     * @param report 校验报告
     * @param item 节点 JSON
     * @param nodeType 节点类型
     * @param key 节点键（可为 {@code null}）
     */
    private static void checkCounterSign(ValidationReport report, JsonNode item, ProcessNodeType nodeType, String key) {
        if (nodeType != ProcessNodeType.COUNTER_SIGN) {
            return;
        }
        JsonNode assignees = item.get("assignees");
        boolean hasAssignees = assignees != null && ((assignees.isArray() && !assignees.isEmpty())
                || (!assignees.isArray() && !assignees.asText().isBlank()));
        if (!hasAssignees) {
            report.add(ValidationIssue.of(ValidationCode.COUNTER_SIGN_ASSIGNEES_REQUIRED, key));
        }
        if (!isValidCounterSignPolicy(text(item, "counterSignPolicy"))) {
            report.add(ValidationIssue.of(ValidationCode.COUNTER_SIGN_POLICY_INVALID, key));
        }
    }

    /**
     * 判断会签策略是否合法（缺省视为 ALL）。
     *
     * @param policy 会签策略文本，可为 {@code null}
     * @return 合法返回 {@code true}
     */
    public static boolean isValidCounterSignPolicy(String policy) {
        return policy == null || policy.isBlank()
                || COUNTER_SIGN_POLICY_PATTERN.matcher(policy.trim().toUpperCase()).matches();
    }

    /**
     * 校验审批节点的表单绑定：编码格式与 formId/formCode 成对性。
     *
     * <p>仅针对 {@link ProcessNodeType#USER_TASK} 节点；非审批节点携带表单字段时仅被忽略不报错。
     * 不做跨服务存在性校验（whale-workflow 不依赖 whale-form）。</p>
     *
     * @param report 校验报告
     * @param item 节点 JSON
     * @param nodeType 节点类型
     * @param key 节点键（用于定位，可为 {@code null}）
     */
    private static void checkUserTaskForm(ValidationReport report, JsonNode item, ProcessNodeType nodeType, String key) {
        if (nodeType != ProcessNodeType.USER_TASK) {
            return;
        }
        String formId = text(item, "formId");
        String formCode = text(item, "formCode");
        boolean hasId = formId != null && !formId.isBlank();
        boolean hasCode = formCode != null && !formCode.isBlank();
        if (hasCode && !FORM_CODE_PATTERN.matcher(formCode.trim()).matches()) {
            report.add(ValidationIssue.of(ValidationCode.USER_TASK_FORM_CODE_INVALID, key));
        }
        if (hasId != hasCode) {
            report.add(ValidationIssue.of(ValidationCode.USER_TASK_FORM_INCOMPLETE, key));
        }
    }

    /**
     * 校验单个节点键（缺失/不合法）。
     *
     * @param report 校验报告
     * @param key 有效节点键（规范化模式下可为 {@code null}）
     * @param index 节点序号（1 起，用于定位）
     */
    private static void checkNodeKey(ValidationReport report, String key, int index) {
        if (key == null) {
            report.add(ValidationIssue.of(ValidationCode.NODE_KEY_MISSING, "第 " + index + " 个节点"));
            return;
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            report.add(ValidationIssue.of(ValidationCode.NODE_KEY_INVALID, key));
        }
    }

    /**
     * 校验流转数组：key 重复、引用缺失、自环、条件警告。
     *
     * @param report 校验报告
     * @param flowsNode 流转 JSON 数组，可为 {@code null}
     * @param graph 图结构累积器
     */
    private static void checkFlows(ValidationReport report, JsonNode flowsNode, Graph graph) {
        if (flowsNode == null || !flowsNode.isArray()) {
            return;
        }
        checkFlowLimit(report, flowsNode);
        Set<String> flowKeys = new LinkedHashSet<>();
        for (JsonNode item : flowsNode) {
            checkFlow(report, item, graph, flowKeys);
        }
    }

    /**
     * 校验流转条数上限。
     *
     * @param report 校验报告
     * @param flowsNode 流转 JSON 数组
     */
    private static void checkFlowLimit(ValidationReport report, JsonNode flowsNode) {
        if (flowsNode.size() > ProcessModelParser.MAX_FLOWS) {
            report.add(ValidationIssue.of(ValidationCode.TOO_MANY_FLOWS,
                    "流转数 " + flowsNode.size() + " 超出上限 " + ProcessModelParser.MAX_FLOWS, null));
        }
    }

    /**
     * 校验单条流转：key 重复、引用缺失、自环、邻接累积、条件警告。
     *
     * @param report 校验报告
     * @param item 流转 JSON
     * @param graph 图结构累积器
     * @param flowKeys 已见流转键集合
     */
    private static void checkFlow(ValidationReport report, JsonNode item, Graph graph, Set<String> flowKeys) {
        String key = text(item, "key");
        if (key != null && !key.isBlank() && !flowKeys.add(key.trim())) {
            report.add(ValidationIssue.of(ValidationCode.FLOW_KEY_DUPLICATE, key.trim()));
        }
        String from = text(item, "from");
        String to = text(item, "to");
        if (!referenceValid(graph, from, to)) {
            report.add(ValidationIssue.of(ValidationCode.FLOW_REFERENCE_MISSING, key));
        }
        if (from != null && from.equals(to)) {
            report.add(ValidationIssue.of(ValidationCode.FLOW_SELF_LOOP, from));
        }
        addEdge(graph, from, to, isDefault(item));
        checkCondition(report, text(item, "condition"), key);
    }

    /**
     * 校验流转条件表达式：长度与 SpEL 语法（只解析不求值）。
     *
     * @param report 校验报告
     * @param condition 条件表达式文本，可为 {@code null}
     * @param flowKey 流转键（定位用）
     */
    private static void checkCondition(ValidationReport report, String condition, String flowKey) {
        if (condition == null || condition.isBlank()) {
            return;
        }
        if (condition.length() > ConditionGuard.MAX_EXPRESSION_LENGTH) {
            report.add(ValidationIssue.of(ValidationCode.CONDITION_TOO_LONG, flowKey));
            return;
        }
        if (!ConditionGuard.isValid(condition)) {
            report.add(ValidationIssue.of(ValidationCode.CONDITION_SYNTAX_INVALID, flowKey));
        }
    }

    /**
     * 判定流转两端引用是否均指向已知节点。
     *
     * @param graph 图结构累积器
     * @param from 源节点键，可为 {@code null}
     * @param to 目标节点键，可为 {@code null}
     * @return 两端均有效返回 {@code true}
     */
    private static boolean referenceValid(Graph graph, String from, String to) {
        return from != null && to != null
                && graph.getNodeKeys().contains(from) && graph.getNodeKeys().contains(to);
    }

    /**
     * 图级校验：开始/结束事件、网关出流与默认分支、悬空、不可达、成环。
     *
     * @param report 校验报告
     * @param graph 图结构
     */
    private static void checkGraph(ValidationReport report, Graph graph) {
        long startCount = graph.getTypes().values().stream()
                .filter(type -> type == ProcessNodeType.START_EVENT).count();
        if (startCount != 1) {
            report.add(ValidationIssue.of(ValidationCode.START_EVENT_COUNT, "实际 " + startCount + " 个", null));
        }
        long endCount = graph.getTypes().values().stream()
                .filter(type -> type == ProcessNodeType.END_EVENT).count();
        if (endCount == 0) {
            report.add(ValidationIssue.of(ValidationCode.END_EVENT_REQUIRED, null));
        }
        for (String key : graph.getNodeKeys()) {
            List<String> outs = graph.getAdjacency().getOrDefault(key, List.of());
            ProcessNodeType type = graph.getTypes().get(key);
            if (type == ProcessNodeType.EXCLUSIVE_GATEWAY) {
                if (outs.isEmpty()) {
                    report.add(ValidationIssue.of(ValidationCode.GATEWAY_NO_OUTGOING, key));
                } else if (outs.size() > 1 && !graph.getDefaultFrom().contains(key)) {
                    report.add(ValidationIssue.of(ValidationCode.GATEWAY_MULTI_BRANCH_NO_DEFAULT, key));
                }
            }
            if (type == ProcessNodeType.PARALLEL_GATEWAY && outs.isEmpty()) {
                report.add(ValidationIssue.of(ValidationCode.GATEWAY_NO_OUTGOING, key));
            }
            if (type != ProcessNodeType.END_EVENT && outs.isEmpty()) {
                report.add(ValidationIssue.of(ValidationCode.DEAD_END, key));
            }
        }
        checkReachable(report, graph);
        checkCycle(report, graph);
    }

    /**
     * 可达性校验：从入口（首个 START_EVENT，否则首节点）BFS，未达节点报 {@code UNREACHABLE_NODE}。
     *
     * @param report 校验报告
     * @param graph 图结构
     */
    private static void checkReachable(ValidationReport report, Graph graph) {
        Set<String> reachable = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        String entry = entryKey(graph);
        if (entry != null) {
            queue.add(entry);
            reachable.add(entry);
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : graph.getAdjacency().getOrDefault(current, List.of())) {
                if (graph.getNodeKeys().contains(next) && reachable.add(next)) {
                    queue.add(next);
                }
            }
        }
        for (String key : graph.getNodeKeys()) {
            if (!reachable.contains(key)) {
                report.add(ValidationIssue.of(ValidationCode.UNREACHABLE_NODE, key));
            }
        }
    }

    /**
     * 环检测：DFS 三色标记，命中回边报 {@code CYCLE_DETECTED}。
     *
     * @param report 校验报告
     * @param graph 图结构
     */
    private static void checkCycle(ValidationReport report, Graph graph) {
        Set<String> done = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (String key : graph.getNodeKeys()) {
            if (!done.contains(key) && hasCycle(graph, key, visiting, done)) {
                report.add(ValidationIssue.of(ValidationCode.CYCLE_DETECTED, key));
                return;
            }
        }
    }

    /**
     * 深度优先检测从 {@code key} 出发是否存在环。
     *
     * @param graph 图结构
     * @param key 起始节点键
     * @param visiting 当前递归栈
     * @param done 已完成节点
     * @return 存在环返回 {@code true}
     */
    private static boolean hasCycle(Graph graph, String key, Set<String> visiting, Set<String> done) {
        if (visiting.contains(key)) {
            return true;
        }
        if (done.contains(key)) {
            return false;
        }
        visiting.add(key);
        for (String next : graph.getAdjacency().getOrDefault(key, List.of())) {
            if (graph.getNodeKeys().contains(next) && hasCycle(graph, next, visiting, done)) {
                return true;
            }
        }
        visiting.remove(key);
        done.add(key);
        return false;
    }

    /**
     * 取图入口：首个 {@code START_EVENT}，否则首个节点。
     *
     * @param graph 图结构
     * @return 入口节点键，无节点返回 {@code null}
     */
    private static String entryKey(Graph graph) {
        for (String key : graph.getNodeKeys()) {
            if (graph.getTypes().get(key) == ProcessNodeType.START_EVENT) {
                return key;
            }
        }
        return graph.getNodeKeys().isEmpty() ? null : graph.getNodeKeys().iterator().next();
    }

    /**
     * 追加邻接边并记录源节点是否含默认流转。
     *
     * @param graph 图结构
     * @param flow 流程模型流转
     */
    private static void addEdge(Graph graph, ProcessModelFlow flow) {
        addEdge(graph, flow.getFrom(), flow.getTo(), Boolean.TRUE.equals(flow.getIsDefault()));
    }

    /**
     * 追加邻接边并记录源节点是否含默认流转。
     *
     * @param graph 图结构
     * @param from 源节点键，可为 {@code null}
     * @param to 目标节点键，可为 {@code null}
     * @param isDefault 是否默认流转
     */
    private static void addEdge(Graph graph, String from, String to, boolean isDefault) {
        if (from == null || to == null) {
            return;
        }
        graph.getAdjacency().computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        if (isDefault) {
            graph.getDefaultFrom().add(from);
        }
    }

    /**
     * 读取流转的 {@code isDefault} 布尔值。
     *
     * @param item 流转 JSON
     * @return 默认流转返回 {@code true}
     */
    private static boolean isDefault(JsonNode item) {
        JsonNode value = item.get("isDefault");
        return value != null && !value.isNull() && value.asBoolean(false);
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
     * 图结构累积器：节点键集合、类型映射、邻接表、默认流转源集合。
     *
     * @author BlueWhale
     */
    @Data
    private static class Graph {

        /** 节点键集合（保持定义顺序） */
        private Set<String> nodeKeys = new LinkedHashSet<>();

        /** 节点键 → 节点类型 */
        private Map<String, ProcessNodeType> types = new LinkedHashMap<>();

        /** 邻接表：源节点键 → 目标节点键列表（保持流转顺序） */
        private Map<String, List<String>> adjacency = new LinkedHashMap<>();

        /** 含默认流转的源节点键集合 */
        private Set<String> defaultFrom = new LinkedHashSet<>();
    }
}
