package com.bluewhale.workflow.support.machine;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelException;
import com.bluewhale.workflow.support.ProcessModelFlow;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.ValidationCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用状态机内核：以节点为状态、以流转为迁移，驱动流程实例推进。
 *
 * <p>选路规则（Wave 5.35 起）：</p>
 * <ol>
 *   <li>出流为空 → 终止；</li>
 *   <li>出流唯一 → 该流（若带条件且求值为假，则视为终止）；</li>
 *   <li>多出流且存在条件 → 按定义顺序求值，唯一命中即为所选；命中多个取首个并告警；
 *       全部未命中则回落 {@code isDefault}；仍无则抛 {@link ValidationCode#NO_MATCHING_FLOW}；</li>
 *   <li>多出流且无条件（legacy） → 取 {@code isDefault}，缺省抛
 *       {@link ValidationCode#GATEWAY_MULTI_BRANCH_NO_DEFAULT}。</li>
 * </ol>
 *
 * <p>{@link #advance(String)} 从给定状态起，把 {@code START_EVENT}/网关作为透传节点逐跳跳过，
 * 停在首个审批类节点（{@code USER_TASK}/{@code COUNTER_SIGN}）或终止态；并行网关多出流（fork）
 * 时逐分支推进并汇总全部审批节点。单次推进的透传跳数上限 {@link #MAX_GATEWAY_HOPS}。</p>
 *
 * @author BlueWhale
 */
public class StateMachine {

    /** 单次推进的透传（网关/事件）跳数上限 */
    public static final int MAX_GATEWAY_HOPS = 16;

    /** 超限文案 */
    private static final String HOPS_EXCEEDED_MESSAGE = "网关流转超出上限";

    /** 日志 */
    private static final Logger LOG = LoggerFactory.getLogger(StateMachine.class);

    /** 流程模型 */
    private final ProcessModel model;

    /** 流程变量（条件守卫求值） */
    private final Map<String, Object> variables;

    /**
     * 构造状态机（无流程变量，兼容旧调用）。
     *
     * @param model 流程模型
     */
    public StateMachine(ProcessModel model) {
        this(model, Map.of());
    }

    /**
     * 构造状态机。
     *
     * @param model 流程模型
     * @param variables 流程变量，可为 {@code null}
     */
    public StateMachine(ProcessModel model, Map<String, Object> variables) {
        this.model = model;
        this.variables = variables == null ? Map.of() : variables;
    }

    /**
     * 列出某节点的全部可选迁移（按定义顺序）。
     *
     * <p>守卫绑定：带条件 → {@link ConditionGuard}；默认流转 → {@link DefaultFlowGuard}；
     * 其余 → {@link AlwaysGuard}。</p>
     *
     * @param nodeKey 节点键
     * @return 迁移列表（无出流时为空列表）
     */
    public List<Transition> transitions(String nodeKey) {
        List<Transition> result = new ArrayList<>();
        for (ProcessModelFlow flow : model.outgoing(nodeKey)) {
            result.add(new Transition(flow, guardOf(flow)));
        }
        return result;
    }

    /**
     * 按条件/默认规则选出唯一迁移（使用空流程变量）。
     *
     * @param nodeKey 节点键
     * @return 选中迁移；无出流或无匹配返回 {@code null}
     */
    public Transition pickTransition(String nodeKey) {
        return pickTransition(nodeKey, context(nodeKey, StateMachineEvent.APPROVE));
    }

    /**
     * 按条件/默认规则选出唯一迁移。
     *
     * @param nodeKey 节点键
     * @param guardContext 守卫上下文（携带流程变量）
     * @return 选中迁移；无出流或无匹配返回 {@code null}
     * @throws ProcessModelException 多分支无条件且缺默认流转、或条件均未命中且无默认流转时抛出
     */
    public Transition pickTransition(String nodeKey, GuardContext guardContext) {
        List<Transition> transitions = transitions(nodeKey);
        if (transitions.isEmpty()) {
            return null;
        }
        if (transitions.size() == 1) {
            Transition only = transitions.get(0);
            String condition = only.getFlow().getCondition();
            if (hasText(condition) && !only.matches(guardContext)) {
                return null;
            }
            return only;
        }
        boolean conditional = transitions.stream().anyMatch(item -> hasText(item.getFlow().getCondition()));
        if (!conditional) {
            for (Transition transition : transitions) {
                if (Boolean.TRUE.equals(transition.getFlow().getIsDefault())) {
                    return transition;
                }
            }
            throw new ProcessModelException(ValidationCode.GATEWAY_MULTI_BRANCH_NO_DEFAULT,
                    "流程定义非法：" + ValidationCode.GATEWAY_MULTI_BRANCH_NO_DEFAULT.name(), nodeKey);
        }
        List<Transition> matched = new ArrayList<>();
        for (Transition transition : transitions) {
            if (hasText(transition.getFlow().getCondition()) && transition.matches(guardContext)) {
                matched.add(transition);
            }
        }
        if (matched.size() == 1) {
            return matched.get(0);
        }
        if (matched.size() > 1) {
            LOG.warn("多个流转条件同时命中，按定义顺序取首个: node={}, flow={}", nodeKey, matched.get(0).getFlow().getKey());
            return matched.get(0);
        }
        for (Transition transition : transitions) {
            if (Boolean.TRUE.equals(transition.getFlow().getIsDefault())) {
                return transition;
            }
        }
        throw new ProcessModelException(ValidationCode.NO_MATCHING_FLOW,
                "无匹配的流转分支：" + nodeKey, nodeKey);
    }

    /**
     * 从给定状态推进：跳过透传节点，停在首个审批节点或终止态。
     *
     * @param fromKey 起始节点键（入口 {@code START_EVENT} 或审批节点）
     * @return 迁移结果（可能含多个审批节点，对应并行 fork）
     * @throws ProcessModelException 透传跳数超限时抛出
     */
    public TransitionResult advance(String fromKey) {
        List<ProcessModelNode> active = new ArrayList<>();
        List<ProcessModelNode> ccNodes = new ArrayList<>();
        String last = trace(fromKey, 0, active, ccNodes);
        if (active.isEmpty()) {
            return TransitionResult.terminated(last, model.node(last));
        }
        return TransitionResult.at(active, ccNodes);
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
        Transition transition = pickTransition(currentKey, context(currentKey, StateMachineEvent.APPROVE));
        if (transition == null) {
            return TransitionResult.terminated(currentKey, model.node(currentKey));
        }
        return advance(transition.to());
    }

    /**
     * 逐跳推进并收集审批节点；并行网关多出流时分枝推进。
     *
     * <p>抄送节点（{@code CC_TASK}）为非阻塞节点：收集到抄送列表后继续沿出流推进，
     * 不加入审批节点列表，因此不会产生待办。</p>
     *
     * @param key 当前节点键
     * @param hops 已消耗透传跳数
     * @param active 审批节点收集器
     * @param ccNodes 抄送节点收集器
     * @return 最后停留的节点键
     */
    private String trace(String key, int hops, List<ProcessModelNode> active, List<ProcessModelNode> ccNodes) {
        if (hops > MAX_GATEWAY_HOPS) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), HOPS_EXCEEDED_MESSAGE);
        }
        ProcessModelNode node = model.node(key);
        if (node == null) {
            return key;
        }
        if (node.getType() != null && node.getType().isCopy()) {
            ccNodes.add(node);
        }
        if (node.getType() != null && node.getType().isApproval()) {
            active.add(node);
            return key;
        }
        if (node.getType() == ProcessNodeType.END_EVENT) {
            return key;
        }
        List<Transition> transitions = transitions(key);
        if (transitions.isEmpty()) {
            return key;
        }
        if (node.getType() == ProcessNodeType.PARALLEL_GATEWAY && transitions.size() > 1) {
            for (Transition transition : transitions) {
                trace(transition.to(), hops + 1, active, ccNodes);
            }
            return key;
        }
        Transition picked = pickTransition(key, context(key, StateMachineEvent.COMPLETE));
        if (picked == null) {
            return key;
        }
        return trace(picked.to(), hops + 1, active, ccNodes);
    }

    /**
     * 构造节点守卫上下文。
     *
     * @param nodeKey 节点键
     * @param event 触发事件
     * @return 守卫上下文
     */
    private GuardContext context(String nodeKey, StateMachineEvent event) {
        return GuardContext.of(model, model.node(nodeKey), event, variables);
    }

    /**
     * 按流转配置选择守卫。
     *
     * @param flow 流转
     * @return 守卫实例
     */
    private static Guard guardOf(ProcessModelFlow flow) {
        if (hasText(flow.getCondition())) {
            return ConditionGuard.of(flow.getCondition());
        }
        return Boolean.TRUE.equals(flow.getIsDefault()) ? DefaultFlowGuard.INSTANCE : AlwaysGuard.INSTANCE;
    }

    /**
     * 判断文本非空。
     *
     * @param value 文本
     * @return 非空返回 {@code true}
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
