package com.bluewhale.workflow.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程路径回溯：从当前节点沿流转反向查找上游审批节点。
 *
 * <p>用于「驳回到上一节点 / 指定节点」与会签失败后的自动退回目标。
 * 网关、抄送、开始/结束等非审批节点会被跳过；按 BFS 层级返回，
 * 保证「上一节点」取的是最近一层，而不是定义顺序上的前一个。</p>
 *
 * @author BlueWhale
 */
public final class ProcessPaths {

    /** 工具类不允许实例化 */
    private ProcessPaths() {
    }

    /**
     * 查找全部上游审批节点（去重，按定义顺序）。
     *
     * @param model 流程模型
     * @param currentKey 当前节点键
     * @return 上游审批节点列表；无上游返回空列表
     */
    public static List<ProcessModelNode> ancestorApprovalNodes(ProcessModel model, String currentKey) {
        Set<String> keys = new LinkedHashSet<>();
        for (List<ProcessModelNode> level : approvalLevels(model, currentKey)) {
            for (ProcessModelNode node : level) {
                keys.add(node.getKey());
            }
        }
        List<ProcessModelNode> result = new ArrayList<>();
        for (String key : keys) {
            ProcessModelNode node = model.node(key);
            if (node != null) {
                result.add(node);
            }
        }
        result.sort(Comparator.comparingInt(node -> model.indexOf(node.getKey())));
        return result;
    }

    /**
     * 查找最近的上一层审批节点（BFS 第一个命中层级）。
     *
     * @param model 流程模型
     * @param currentKey 当前节点键
     * @return 最近一层的审批节点；无上游返回空列表
     */
    public static List<ProcessModelNode> nearestPreviousApprovalNodes(ProcessModel model, String currentKey) {
        for (List<ProcessModelNode> level : approvalLevels(model, currentKey)) {
            if (!level.isEmpty()) {
                List<ProcessModelNode> sorted = new ArrayList<>(level);
                sorted.sort(Comparator.comparingInt(node -> model.indexOf(node.getKey())));
                return sorted;
            }
        }
        return List.of();
    }

    /**
     * 按 BFS 层级返回上游审批节点：每层只包含该深度发现的审批节点。
     *
     * @param model 流程模型
     * @param currentKey 当前节点键
     * @return 层级列表（可能含空层）
     */
    private static List<List<ProcessModelNode>> approvalLevels(ProcessModel model, String currentKey) {
        List<List<ProcessModelNode>> levels = new ArrayList<>();
        if (currentKey == null || model == null || model.node(currentKey) == null) {
            return levels;
        }
        Map<String, List<String>> incoming = new HashMap<>();
        for (ProcessModelFlow flow : model.getFlows()) {
            if (flow.getTo() == null || flow.getFrom() == null) {
                continue;
            }
            incoming.computeIfAbsent(flow.getTo(), key -> new ArrayList<>()).add(flow.getFrom());
        }
        Set<String> visited = new HashSet<>();
        visited.add(currentKey);
        List<String> frontier = List.of(currentKey);
        while (!frontier.isEmpty()) {
            List<ProcessModelNode> approvals = new ArrayList<>();
            List<String> next = new ArrayList<>();
            for (String key : frontier) {
                for (String from : incoming.getOrDefault(key, List.of())) {
                    if (!visited.add(from)) {
                        continue;
                    }
                    ProcessModelNode node = model.node(from);
                    if (node == null) {
                        continue;
                    }
                    if (node.isUserTask()) {
                        approvals.add(node);
                    }
                    // 审批节点也要继续向上回溯，否则「指定节点」只能看到相邻一层
                    next.add(from);
                }
            }
            levels.add(approvals);
            frontier = next;
        }
        return levels;
    }
}
