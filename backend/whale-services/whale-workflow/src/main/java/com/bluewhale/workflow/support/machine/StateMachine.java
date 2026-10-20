package com.bluewhale.workflow.support.machine;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelException;
import com.bluewhale.workflow.support.ProcessModelFlow;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.ValidationCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用状态机内核：以节点为状态、以流转为迁移，驱动流程实例推进。
 *
 * <p>选路规则（冻结契约 §3.2）：出流 0 → 终止；出流 1 → 该流；出流 ≥2 → 取
 * {@code isDefault=true} 的流（缺省则抛 {@code GATEWAY_MULTI_BRANCH_NO_DEFAULT}）。
 * 条件本轮<b>不参与选路</b>，仅在 {@code Guard} SPI（{@link AlwaysGuard}/{@link DefaultFlowGuard}）
 * 上预留扩展点。</p>
 *
 * <p>{@link #advance(String)} 从给定状态起，把 {@code START_EVENT}/{@code EXCLUSIVE_GATEWAY}
 * 作为透传节点逐跳跳过，停在首个 {@code USER_TASK} 或终止态；单次推进的透传跳数上限
 * {@link #MAX_GATEWAY_HOPS}，超限抛 {@code PARAM_ERROR}「网关流转超出上限」。</p>
 *
 * @author BlueWhale
 */
public class StateMachine {

    /** 单次推进的透传（网关/事件）跳数上限 */
    public static final int MAX_GATEWAY_HOPS = 16;

    /** 超限文案 */
    private static final String HOPS_EXCEEDED_MESSAGE = "网关流转超出上限";

    /** 流程模型 */
    private final ProcessModel model;

    /**
     * 构造状态机。
     *
     * @param model 流程模型
     */
    public StateMachine(ProcessModel model) {
        this.model = model;
    }

    /**
     * 列出某节点的全部可选迁移（按定义顺序，默认流转挂 {@link DefaultFlowGuard}，其余挂 {@link AlwaysGuard}）。
     *
     * @param nodeKey 节点键
     * @return 迁移列表（无出流时为空列表）
     */
    public List<Transition> transitions(String nodeKey) {
        List<Transition> result = new ArrayList<>();
        for (ProcessModelFlow flow : model.outgoing(nodeKey)) {
            Guard guard = Boolean.TRUE.equals(flow.getIsDefault()) ? DefaultFlowGuard.INSTANCE : AlwaysGuard.INSTANCE;
            result.add(new Transition(flow, guard));
        }
        return result;
    }

    /**
     * 按冻结选路规则选出唯一迁移。
     *
     * @param nodeKey 节点键
     * @return 选中迁移；无出流返回 {@code null}
     * @throws ProcessModelException 多分支缺少默认流转时抛出
     */
    public Transition pickTransition(String nodeKey) {
        List<Transition> transitions = transitions(nodeKey);
        if (transitions.isEmpty()) {
            return null;
        }
        if (transitions.size() == 1) {
            return transitions.get(0);
        }
        for (Transition transition : transitions) {
            if (Boolean.TRUE.equals(transition.getFlow().getIsDefault())) {
                return transition;
            }
        }
        throw new ProcessModelException(ValidationCode.GATEWAY_MULTI_BRANCH_NO_DEFAULT,
                "流程定义非法：" + ValidationCode.GATEWAY_MULTI_BRANCH_NO_DEFAULT.name(), nodeKey);
    }

    /**
     * 从给定状态推进：跳过透传节点，停在首个审批节点或终止态。
     *
     * @param fromKey 起始节点键（入口 {@code START_EVENT} 或审批节点）
     * @return 迁移结果
     * @throws ProcessModelException 透传跳数超限时抛出
     */
    public TransitionResult advance(String fromKey) {
        String current = fromKey;
        for (int hop = 0; hop <= MAX_GATEWAY_HOPS; hop++) {
            ProcessModelNode node = model.node(current);
            if (node == null) {
                return TransitionResult.terminated(current, null);
            }
            if (node.getType() == ProcessNodeType.USER_TASK) {
                return TransitionResult.at(current, node);
            }
            if (node.getType() == ProcessNodeType.END_EVENT) {
                return TransitionResult.terminated(current, node);
            }
            Transition transition = pickTransition(current);
            if (transition == null) {
                return TransitionResult.terminated(current, node);
            }
            current = transition.to();
        }
        throw new BizException(ErrorCode.PARAM_ERROR.getCode(), HOPS_EXCEEDED_MESSAGE);
    }

    /**
     * 从流程入口推进（入口为 {@code START_EVENT} 时透传至首个审批节点）。
     *
     * @return 迁移结果
     */
    public TransitionResult start() {
        return advance(model.entryKey());
    }

    /**
     * 同意：从当前审批节点出流，透传至下一个审批节点或终止态。
     *
     * @param currentKey 当前审批节点键
     * @return 迁移结果（无出流时终止）
     */
    public TransitionResult approve(String currentKey) {
        Transition transition = pickTransition(currentKey);
        if (transition == null) {
            return TransitionResult.terminated(currentKey, model.node(currentKey));
        }
        return advance(transition.to());
    }
}
