package com.bluewhale.workflow.support;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程模型（规范化 JSON 的内存表示）：节点集合 + 流转集合。
 *
 * <p>由 {@link ProcessModelParser} 从 {@code definition_json} 解析而来，是状态机内核
 * ({@code support.machine.StateMachine}) 的唯一输入。若来源为 legacy
 * {@code {"nodes":[…]}}，则 {@link #legacy} 为 {@code true}，节点被归一化为顺序
 * {@code USER_TASK} 并自动补 {@code i→i+1} 顺序流，保证与 Wave 5.25 语义一致。</p>
 *
 * @author BlueWhale
 */
@Data
public class ProcessModel {

    /** 定义类型文本（规范化模型为 {@code stateMachine}；legacy 为 {@code null}） */
    private String definitionType;

    /** 是否为 legacy 定义（旧 {@code {"nodes":[…]}} 归一化而来） */
    private boolean legacy;

    /** 有序节点集合 */
    private List<ProcessModelNode> nodes = new ArrayList<>();

    /** 流转集合 */
    private List<ProcessModelFlow> flows = new ArrayList<>();

    /** 节点键 → 节点（解析期构建），便于 O(1) 检索 */
    private transient Map<String, ProcessModelNode> nodeIndex = new LinkedHashMap<>();

    /**
     * 流程入口节点键：优先取首个 {@code START_EVENT}，否则取首个节点（legacy 即 {@code nodes[0]}）。
     *
     * @return 入口节点键；无节点返回 {@code null}
     */
    public String entryKey() {
        for (ProcessModelNode node : nodes) {
            if (node.getType() == ProcessNodeType.START_EVENT) {
                return node.getKey();
            }
        }
        return nodes.isEmpty() ? null : nodes.get(0).getKey();
    }

    /**
     * 按键查询节点。
     *
     * @param key 节点键
     * @return 命中节点，未命中返回 {@code null}
     */
    public ProcessModelNode node(String key) {
        return key == null ? null : nodeIndex.get(key);
    }

    /**
     * 按下标查询节点（legacy 的 {@code currentIndex} 语义；越界返回 {@code null}）。
     *
     * @param index 节点下标（0 起）
     * @return 命中节点，越界返回 {@code null}
     */
    public ProcessModelNode nodeAt(int index) {
        if (index < 0 || index >= nodes.size()) {
            return null;
        }
        return nodes.get(index);
    }

    /**
     * 按节点键查询其在 {@link #nodes} 中的下标（用于遗留的 {@code currentIndex} 语义）。
     *
     * @param key 节点键
     * @return 节点下标，未命中返回 {@code -1}
     */
    public int indexOf(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < nodes.size(); i++) {
            if (key.equals(nodes.get(i).getKey())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查询节点的全部出流（保持定义顺序，供状态机选路使用）。
     *
     * @param key 节点键
     * @return 出流列表（非空集合，无出流时为空列表）
     */
    public List<ProcessModelFlow> outgoing(String key) {
        List<ProcessModelFlow> result = new ArrayList<>();
        if (key == null) {
            return result;
        }
        for (ProcessModelFlow flow : flows) {
            if (key.equals(flow.getFrom())) {
                result.add(flow);
            }
        }
        return result;
    }

    /**
     * 重建节点键索引（解析期与测试构造模型后调用）。
     */
    public void reindex() {
        Map<String, ProcessModelNode> index = new LinkedHashMap<>();
        for (ProcessModelNode node : nodes) {
            if (node.getKey() != null) {
                index.put(node.getKey(), node);
            }
        }
        this.nodeIndex = index;
    }
}
