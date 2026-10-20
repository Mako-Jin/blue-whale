package com.bluewhale.workflow.support.machine;

import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelFlow;
import com.bluewhale.workflow.support.ProcessModelNode;

/**
 * 迁移守卫上下文：承载守卫判定所需的流程模型、当前节点与事件。
 *
 * @author BlueWhale
 */
public class GuardContext {

    /** 流程模型 */
    private final ProcessModel model;

    /** 当前节点 */
    private final ProcessModelNode node;

    /** 触发事件 */
    private final StateMachineEvent event;

    /**
     * 构造守卫上下文。
     *
     * @param model 流程模型
     * @param node 当前节点
     * @param event 触发事件
     */
    private GuardContext(ProcessModel model, ProcessModelNode node, StateMachineEvent event) {
        this.model = model;
        this.node = node;
        this.event = event;
    }

    /**
     * 构造守卫上下文。
     *
     * @param model 流程模型
     * @param node 当前节点，可为 {@code null}
     * @param event 触发事件，可为 {@code null}
     * @return 守卫上下文
     */
    public static GuardContext of(ProcessModel model, ProcessModelNode node, StateMachineEvent event) {
        return new GuardContext(model, node, event);
    }

    /**
     * 获取流程模型。
     *
     * @return 流程模型
     */
    public ProcessModel getModel() {
        return model;
    }

    /**
     * 获取当前节点。
     *
     * @return 当前节点，可为 {@code null}
     */
    public ProcessModelNode getNode() {
        return node;
    }

    /**
     * 获取触发事件。
     *
     * @return 触发事件，可为 {@code null}
     */
    public StateMachineEvent getEvent() {
        return event;
    }

    /**
     * 判断某条流转是否被本上下文接受（守卫的判定入口）。
     *
     * @param guard 守卫
     * @param flow 待判定流转
     * @return 接受返回 {@code true}
     */
    public boolean accepts(Guard guard, ProcessModelFlow flow) {
        return guard != null && guard.accept(this, flow);
    }
}
