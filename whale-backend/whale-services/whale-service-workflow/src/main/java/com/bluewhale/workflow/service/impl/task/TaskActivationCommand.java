package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.service.ProcessCcService;
import com.bluewhale.workflow.service.ProcessDelegateService;
import com.bluewhale.workflow.support.AssigneeOverrides;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.WorkflowConstants;
import com.bluewhale.workflow.support.machine.TransitionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 任务激活命令：流程推进抵达审批节点后，统一创建待办与抄送。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>审批节点 → 建待办（会签按 {@code assignees} 展开；审批人覆盖优先于定义中的静态审批人）；</li>
 *   <li>抄送节点 → 只写抄送记录，<b>不建待办</b>；</li>
 *   <li>每条待办创建时解析一次代理规则并改派，不判断代理人自身是否还有代理，禁止链式。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class TaskActivationCommand {

    /** 待办任务 Mapper */
    private final ProcessTaskMapper processTaskMapper;

    /** 代理规则服务 */
    private final ProcessDelegateService processDelegateService;

    /** 抄送服务 */
    private final ProcessCcService processCcService;

    /**
     * 激活一次推进抵达的节点。
     *
     * @param instance 流程实例（需已落库，且携带 {@code assigneeOverridesJson} 快照）
     * @param model 流程模型
     * @param result 迁移结果
     */
    public void activate(ProcessInstancePo instance, ProcessModel model, TransitionResult result) {
        if (!result.isUserTask()) {
            return;
        }
        Map<String, List<String>> overrides = AssigneeOverrides.parse(instance.getAssigneeOverridesJson());
        for (ProcessModelNode node : result.getNodes()) {
            createNodeTasks(instance, model, node, overrides);
        }
        for (ProcessModelNode node : result.getCcNodes()) {
            createNodeCc(instance, node, overrides);
        }
    }

    /**
     * 为单个审批节点创建待办。
     *
     * @param instance 流程实例
     * @param model 流程模型
     * @param node 审批节点
     * @param overrides 审批人覆盖
     */
    private void createNodeTasks(ProcessInstancePo instance, ProcessModel model,
                                 ProcessModelNode node, Map<String, List<String>> overrides) {
        int nodeIndex = model.indexOf(node.getKey());
        List<String> override = overrides.get(node.getKey());
        if (override != null && !override.isEmpty()) {
            for (String account : override) {
                createTask(instance, nodeIndex, node, account);
            }
            return;
        }
        if (node.getType() == ProcessNodeType.COUNTER_SIGN) {
            List<String> assignees = node.getAssignees();
            if (assignees == null || assignees.isEmpty()) {
                throw missingAssignee(node);
            }
            for (String assignee : assignees) {
                createTask(instance, nodeIndex, node, assignee);
            }
            return;
        }
        String assignee = node.getAssignee();
        if (assignee == null || assignee.isBlank()) {
            throw missingAssignee(node);
        }
        createTask(instance, nodeIndex, node, assignee);
    }

    /**
     * 构造审批人缺失异常。
     *
     * @param node 缺少审批人的节点
     * @return 参数错误异常（消息携带节点键）
     */
    private static BizException missingAssignee(ProcessModelNode node) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程节点未配置审批人：" + node.getKey());
    }

    /**
     * 为单个抄送节点写入抄送记录（覆盖优先）。
     *
     * @param instance 流程实例
     * @param node 抄送节点
     * @param overrides 审批人覆盖
     */
    private void createNodeCc(ProcessInstancePo instance, ProcessModelNode node,
                              Map<String, List<String>> overrides) {
        List<String> override = overrides.get(node.getKey());
        List<String> recipients = override != null && !override.isEmpty() ? override : node.getAssignees();
        processCcService.createNodeCc(instance.getId(), node.getKey(), node.getName(), recipients);
    }

    /**
     * 创建单条待办，并在创建时解析一次代理规则。
     *
     * @param instance 流程实例
     * @param nodeIndex 节点下标
     * @param node 审批节点
     * @param account 原始审批人账号
     */
    private void createTask(ProcessInstancePo instance, int nodeIndex, ProcessModelNode node, String account) {
        String assignee = account;
        String delegateFrom = null;
        LocalDateTime now = LocalDateTime.now();
        if (account != null && !account.isBlank()) {
            Optional<String> delegate = processDelegateService.resolveActive(
                    instance.getDefinitionCode(), account, now);
            if (delegate.isPresent() && !delegate.get().isBlank() && !delegate.get().equals(account)) {
                delegateFrom = account;
                assignee = delegate.get().trim();
            }
        }
        ProcessTaskPo task = new ProcessTaskPo();
        task.setInstanceId(instance.getId());
        task.setNodeIndex(nodeIndex);
        task.setNodeName(node.getName());
        task.setNodeKey(node.getKey());
        task.setFormId(node.getFormId());
        task.setFormCode(node.getFormCode());
        task.setAssignee(assignee);
        task.setOwnerId(account);
        task.setDelegateFrom(delegateFrom);
        task.setStatus(WorkflowConstants.TASK_PENDING);
        task.setDueAt(dueAt(node, now));
        if (node.getType() == ProcessNodeType.COUNTER_SIGN) {
            task.setSignType(ProcessNodeType.COUNTER_SIGN.name());
            task.setSignPolicy(node.getCounterSignPolicy() == null || node.getCounterSignPolicy().isBlank()
                    ? WorkflowConstants.POLICY_ALL : node.getCounterSignPolicy());
        }
        processTaskMapper.insert(task);
    }

    /**
     * 按节点超时时长推算到期时间。
     *
     * @param node 审批节点
     * @param now 待办创建时间
     * @return 到期时间；节点未配置超时返回 {@code null}
     */
    private static LocalDateTime dueAt(ProcessModelNode node, LocalDateTime now) {
        Integer hours = node.getTimeoutHours();
        return hours == null || hours <= 0 ? null : now.plusHours(hours);
    }
}
