package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessNodeType;
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
     * 会签策略判定：节点待办是否已满足完成条件（ANY/RATIO 达成时取消其余待办）。
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 会签节点键
     * @param node 会签节点
     * @return 满足返回 {@code true}
     */
    private boolean counterSignSatisfied(Long instanceId, String nodeKey, ProcessModelNode node) {
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTask::getInstanceId, instanceId);
        wrapper.eq(ProcessTask::getNodeKey, nodeKey);
        List<ProcessTask> tasks = processTaskMapper.selectList(wrapper);
        long total = tasks.size();
        long approved = tasks.stream().filter(item -> isStatus(item, TaskConstants.TASK_APPROVED)).count();
        long pending = tasks.stream().filter(item -> isStatus(item, TaskConstants.TASK_PENDING)).count();
        String policy = policyOf(node);
        if (policy.startsWith(TaskConstants.POLICY_RATIO_PREFIX)) {
            int percent = parseRatio(policy);
            if (approved * TaskConstants.PERCENT_BASE >= total * percent) {
                cancelPendingTasksOfNode(instanceId, nodeKey);
                return true;
            }
            return false;
        }
        if (TaskConstants.POLICY_ANY.equals(policy)) {
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
        return policy == null || policy.isBlank() ? TaskConstants.POLICY_ALL : policy.trim().toUpperCase();
    }

    /**
     * 解析 RATIO:n 的百分比阈值。
     *
     * @param policy 策略文本（形如 {@code RATIO:60}）
     * @return 百分比；非法时回落 100
     */
    private static int parseRatio(String policy) {
        try {
            return Integer.parseInt(policy.substring(TaskConstants.POLICY_RATIO_PREFIX.length()));
        } catch (NumberFormatException e) {
            return TaskConstants.PERCENT_BASE;
        }
    }

    /**
     * 判断任务状态是否等于指定值。
     *
     * @param task 任务
     * @param status 状态值
     * @return 相等返回 {@code true}
     */
    private static boolean isStatus(ProcessTask task, int status) {
        return task.getStatus() != null && task.getStatus() == status;
    }

    /**
     * 取消同节点其余待办（会签 ANY/RATIO 达成时收敛）。
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 节点键
     */
    private void cancelPendingTasksOfNode(Long instanceId, String nodeKey) {
        ProcessTask update = new ProcessTask();
        update.setStatus(TaskConstants.TASK_CANCELED);
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTask::getInstanceId, instanceId);
        wrapper.eq(ProcessTask::getNodeKey, nodeKey);
        wrapper.eq(ProcessTask::getStatus, TaskConstants.TASK_PENDING);
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
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTask::getInstanceId, instanceId);
        wrapper.eq(ProcessTask::getNodeKey, nodeKey);
        wrapper.eq(ProcessTask::getStatus, TaskConstants.TASK_PENDING);
        Long pending = processTaskMapper.selectCount(wrapper);
        return pending != null && pending > 0;
    }
}
