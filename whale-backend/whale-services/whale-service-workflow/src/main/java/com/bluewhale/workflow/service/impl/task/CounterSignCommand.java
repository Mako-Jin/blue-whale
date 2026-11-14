package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.WorkflowConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 会签判定命令：判断节点是否仍需等待其余审批人，并在 ANY/RATIO 达成时收敛同节点待办。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class CounterSignCommand {

    private final ProcessTaskMapper processTaskMapper;

    /**
     * 判定当前同意是否仍需等待同节点其他待办：会签走策略判定，普通/加签节点有待办即等待。
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 节点键
     * @param node 流程模型节点（可为 {@code null}）
     * @return 需等待返回 {@code true}
     */
    public boolean awaitSameNode(Long instanceId, String nodeKey, ProcessModelNode node) {
        if (node != null && node.getType() == ProcessNodeType.COUNTER_SIGN) {
            return !counterSignSatisfied(instanceId, nodeKey, node);
        }
        return hasPendingTasksOfNode(instanceId, nodeKey);
    }

    /**
     * 会签节点驳回后是否需要退回（按策略判定）。
     *
     * <p>调用时驳回任务已置为「已驳回」，因此 {@code pending} 不含本次驳回人：</p>
     * <ul>
     *   <li>{@code ALL}（缺省）：一人驳回即失败，立即退回；</li>
     *   <li>{@code ANY}：已有人同意或仍有待办则继续等待；全部驳回才失败；</li>
     *   <li>{@code RATIO:n}：若同意 + 待办已无法达到阈值则立即失败，否则继续等待。</li>
     * </ul>
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 会签节点键
     * @param node 会签节点
     * @return 需要退回返回 {@code true}；仍需等待返回 {@code false}
     */
    public boolean shouldReturnOnReject(Long instanceId, String nodeKey, ProcessModelNode node) {
        List<ProcessTaskPo> tasks = tasksOfNode(instanceId, nodeKey);
        long total = tasks.stream().filter(CounterSignCommand::isEffective).count();
        long approved = tasks.stream().filter(item -> isStatus(item, WorkflowConstants.TASK_APPROVED)).count();
        long pending = tasks.stream().filter(item -> isStatus(item, WorkflowConstants.TASK_PENDING)).count();
        String policy = policyOf(node);
        if (policy.startsWith(WorkflowConstants.POLICY_RATIO_PREFIX)) {
            long required = total * parseRatio(policy);
            return approved * WorkflowConstants.PERCENT_BASE < required
                    && (approved + pending) * WorkflowConstants.PERCENT_BASE < required;
        }
        if (WorkflowConstants.POLICY_ANY.equals(policy)) {
            return approved == 0 && pending == 0;
        }
        return true;
    }

    /**
     * 查询同节点全部任务。
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 节点键
     * @return 任务列表
     */
    private List<ProcessTaskPo> tasksOfNode(Long instanceId, String nodeKey) {
        LambdaQueryWrapper<ProcessTaskPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTaskPo::getInstanceId, instanceId);
        wrapper.eq(ProcessTaskPo::getNodeKey, nodeKey);
        return processTaskMapper.selectList(wrapper);
    }

    /**
     * 会签策略判定：节点待办是否已满足完成条件（ANY/RATIO 达成时取消其余待办）。
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 会签节点键
     * @param node 会签节点
     * @return 满足返回 {@code true}
     */
    private boolean counterSignSatisfied(Long instanceId, String nodeKey, ProcessModelNode node) {
        List<ProcessTaskPo> tasks = tasksOfNode(instanceId, nodeKey);
        // 仅统计有效任务：已取消任务（加签取消/上一轮 RATIO 收敛）不参与分母，避免阈值被永久放大
        long total = tasks.stream().filter(CounterSignCommand::isEffective).count();
        long approved = tasks.stream().filter(item -> isStatus(item, WorkflowConstants.TASK_APPROVED)).count();
        long pending = tasks.stream().filter(item -> isStatus(item, WorkflowConstants.TASK_PENDING)).count();
        String policy = policyOf(node);
        if (policy.startsWith(WorkflowConstants.POLICY_RATIO_PREFIX)) {
            int percent = parseRatio(policy);
            if (approved * WorkflowConstants.PERCENT_BASE >= total * percent) {
                cancelPendingTasksOfNode(instanceId, nodeKey);
                return true;
            }
            return false;
        }
        if (WorkflowConstants.POLICY_ANY.equals(policy)) {
            if (approved > 0) {
                cancelPendingTasksOfNode(instanceId, nodeKey);
                return true;
            }
            return false;
        }
        return pending == 0;
    }

    /**
     * 读取节点会签策略（缺省 ALL）。
     *
     * @param node 会签节点
     * @return 大写的策略文本
     */
    private static String policyOf(ProcessModelNode node) {
        String policy = node.getCounterSignPolicy();
        return policy == null || policy.isBlank() ? WorkflowConstants.POLICY_ALL : policy.trim().toUpperCase();
    }

    /**
     * 解析 RATIO:n 的百分比阈值。
     *
     * @param policy 策略文本（形如 {@code RATIO:60}）
     * @return 百分比；非法时回落 100
     */
    private static int parseRatio(String policy) {
        try {
            return Integer.parseInt(policy.substring(WorkflowConstants.POLICY_RATIO_PREFIX.length()));
        } catch (NumberFormatException e) {
            return WorkflowConstants.PERCENT_BASE;
        }
    }

    /**
     * 判断任务是否为参与会签统计的有效任务（排除已取消）。
     *
     * @param task 任务
     * @return 已同意 / 待办 / 已驳回返回 {@code true}
     */
    private static boolean isEffective(ProcessTaskPo task) {
        return isStatus(task, WorkflowConstants.TASK_APPROVED)
                || isStatus(task, WorkflowConstants.TASK_PENDING)
                || isStatus(task, WorkflowConstants.TASK_REJECTED);
    }

    /**
     * 判断任务状态是否等于指定值。
     *
     * @param task 任务
     * @param status 状态值
     * @return 相等返回 {@code true}
     */
    private static boolean isStatus(ProcessTaskPo task, int status) {
        return task.getStatus() != null && task.getStatus() == status;
    }

    /**
     * 取消同节点其余待办（会签 ANY/RATIO 达成时收敛）。
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 节点键
     */
    private void cancelPendingTasksOfNode(Long instanceId, String nodeKey) {
        ProcessTaskPo update = new ProcessTaskPo();
        update.setStatus(WorkflowConstants.TASK_CANCELED);
        LambdaQueryWrapper<ProcessTaskPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTaskPo::getInstanceId, instanceId);
        wrapper.eq(ProcessTaskPo::getNodeKey, nodeKey);
        wrapper.eq(ProcessTaskPo::getStatus, WorkflowConstants.TASK_PENDING);
        processTaskMapper.update(update, wrapper);
    }

    /**
     * 判断同节点是否仍有待办（加签/会签场景需等待其余审批人）。
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 节点键
     * @return 仍有待办返回 {@code true}
     */
    private boolean hasPendingTasksOfNode(Long instanceId, String nodeKey) {
        if (nodeKey == null || nodeKey.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<ProcessTaskPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTaskPo::getInstanceId, instanceId);
        wrapper.eq(ProcessTaskPo::getNodeKey, nodeKey);
        wrapper.eq(ProcessTaskPo::getStatus, WorkflowConstants.TASK_PENDING);
        Long pending = processTaskMapper.selectCount(wrapper);
        return pending != null && pending > 0;
    }
}
