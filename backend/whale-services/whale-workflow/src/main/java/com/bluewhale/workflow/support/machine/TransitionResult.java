package com.bluewhale.workflow.support.machine;

import com.bluewhale.workflow.support.ProcessModelNode;
import lombok.Getter;

/**
 * 迁移结果：状态机一次推进后停留的状态。
 *
 * <p>两种终点：{@link #isUserTask()} 为真表示停在审批节点（需建待办）；
 * {@link #isTerminated()} 为真表示流程终止（到达结束事件或无出流）。</p>
 *
 * @author BlueWhale
 */
@Getter
public class TransitionResult {

    /** 停留状态（节点键） */
    private final String state;

    /** 停留节点，可为 {@code null} */
    private final ProcessModelNode node;

    /** 是否终止（无出流 / 结束事件） */
    private final boolean terminated;

    /**
     * 构造迁移结果。
     *
     * @param state 停留状态（节点键）
     * @param node 停留节点，可为 {@code null}
     * @param terminated 是否终止
     */
    public TransitionResult(String state, ProcessModelNode node, boolean terminated) {
        this.state = state;
        this.node = node;
        this.terminated = terminated;
    }

    /**
     * 构造「停在审批节点」的结果。
     *
     * @param state 节点键
     * @param node 审批节点
     * @return 迁移结果
     */
    public static TransitionResult at(String state, ProcessModelNode node) {
        return new TransitionResult(state, node, false);
    }

    /**
     * 构造「终止」结果。
     *
     * @param state 节点键，可为 {@code null}
     * @param node 终止节点，可为 {@code null}
     * @return 迁移结果
     */
    public static TransitionResult terminated(String state, ProcessModelNode node) {
        return new TransitionResult(state, node, true);
    }

    /**
     * 是否停在审批节点。
     *
     * @return 审批节点返回 {@code true}
     */
    public boolean isUserTask() {
        return !terminated && node != null && node.isUserTask();
    }
}
