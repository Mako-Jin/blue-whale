package com.bluewhale.workflow.support.machine;

import com.bluewhale.workflow.support.ProcessModelNode;
import lombok.Getter;

import java.util.List;

/**
 * 迁移结果：状态机一次推进后停留的状态。
 *
 * <p>{@link #getNodes()} 为该次推进抵达的全部审批节点（order 与定义顺序一致）：
 * 单节点为 1 条，并行网关 fork 后可多于 1 条；为空表示流程终止（到达结束事件或无出流）。</p>
 *
 * @author BlueWhale
 */
@Getter
public class TransitionResult {

    /** 停留状态（首个审批节点键 / 终止节点键） */
    private final String state;

    /** 停留节点（首个），可为 {@code null} */
    private final ProcessModelNode node;

    /** 是否终止（无出流 / 结束事件） */
    private final boolean terminated;

    /** 抵达的全部审批节点（终止时为空列表） */
    private final List<ProcessModelNode> nodes;

    /**
     * 构造迁移结果。
     *
     * @param state 停留状态（节点键）
     * @param node 停留节点，可为 {@code null}
     * @param terminated 是否终止
     * @param nodes 抵达的全部审批节点，可为 {@code null}
     */
    public TransitionResult(String state, ProcessModelNode node, boolean terminated, List<ProcessModelNode> nodes) {
        this.state = state;
        this.node = node;
        this.terminated = terminated;
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    /**
     * 构造「停在审批节点」的结果（单节点）。
     *
     * @param state 节点键
     * @param node 审批节点
     * @return 迁移结果
     */
    public static TransitionResult at(String state, ProcessModelNode node) {
        return new TransitionResult(state, node, false, node == null ? List.of() : List.of(node));
    }

    /**
     * 构造「停在多个审批节点」的结果（并行 fork）。
     *
     * @param nodes 抵达的审批节点列表（非空）
     * @return 迁移结果
     */
    public static TransitionResult at(List<ProcessModelNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return terminated(null, null);
        }
        ProcessModelNode first = nodes.get(0);
        return new TransitionResult(first.getKey(), first, false, nodes);
    }

    /**
     * 构造「终止」结果。
     *
     * @param state 终止节点键，可为 {@code null}
     * @param node 终止节点，可为 {@code null}
     * @return 迁移结果
     */
    public static TransitionResult terminated(String state, ProcessModelNode node) {
        return new TransitionResult(state, node, true, List.of());
    }

    /**
     * 是否停在审批节点。
     *
     * @return 审批节点返回 {@code true}
     */
    public boolean isUserTask() {
        return !terminated && !nodes.isEmpty();
    }
}
