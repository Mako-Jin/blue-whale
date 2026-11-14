package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.WorkflowConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 加签命令：为当前节点追加同级审批人，追加人全部同意后节点才完成。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class AddSignCommand {

    private final ProcessTaskMapper processTaskMapper;

    private final TaskCommandSupport taskCommandSupport;

    /**
     * 加签：为当前节点追加同级审批人，节点需追加人全部同意后才完成。
     *
     * @param id 任务ID
     * @param assignees 加签人账号列表（去空白、去重后非空，单次最多 10 人）
     */
    @Transactional(rollbackFor = Exception.class)
    public void addSign(Long id, List<String> assignees) {
        ProcessTaskPo task = taskCommandSupport.requirePendingTask(id);
        taskCommandSupport.requireRunningInstance(task.getInstanceId());
        List<String> targets = normalizeAddSigners(assignees);
        boolean counterSign = ProcessNodeType.COUNTER_SIGN.name().equals(task.getSignType());
        if (counterSign && !WorkflowConstants.POLICY_ALL.equals(policyOfTask(task))) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "ANY/RATIO 会签节点不支持加签");
        }
        for (ProcessTaskPo existing : listTasksOfNode(task)) {
            if (existing.getStatus() != null && existing.getStatus() == WorkflowConstants.TASK_CANCELED) {
                continue;
            }
            if (existing.getAssignee() != null && targets.contains(existing.getAssignee())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                        "加签人已在本节点任务中：" + existing.getAssignee());
            }
        }
        for (String target : targets) {
            ProcessTaskPo added = new ProcessTaskPo();
            added.setInstanceId(task.getInstanceId());
            added.setNodeIndex(task.getNodeIndex());
            added.setNodeName(task.getNodeName());
            added.setNodeKey(task.getNodeKey());
            added.setFormId(task.getFormId());
            added.setFormCode(task.getFormCode());
            added.setParentNodeKey(task.getParentNodeKey());
            added.setAssignee(target);
            added.setStatus(WorkflowConstants.TASK_PENDING);
            added.setOwnerId(target);
            // 加签沿用同节点的到期时间，避免节点 SLA 因追加审批人而被悄悄放宽
            added.setDueAt(task.getDueAt());
            added.setSignType(counterSign ? ProcessNodeType.COUNTER_SIGN.name() : WorkflowConstants.SIGN_TYPE_ADD_SIGN);
            added.setSignPolicy(counterSign ? WorkflowConstants.POLICY_ALL : null);
            processTaskMapper.insert(added);
        }
    }

    /**
     * 规范化加签人列表：去空白、去重、校验非空与长度/数量上限。
     *
     * @param assignees 原始加签人列表，可为 {@code null}
     * @return 去重后的加签人列表
     * @throws BizException 列表为空、账号过长或超过单次上限时抛出 {@code PARAM_ERROR}
     */
    private static List<String> normalizeAddSigners(List<String> assignees) {
        if (assignees == null || assignees.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "加签人不能为空");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : assignees) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String target = raw.trim();
            if (target.length() > WorkflowConstants.MAX_ASSIGNEE_LENGTH) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "加签人账号长度不能超过 64");
            }
            unique.add(target);
        }
        if (unique.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "加签人不能为空");
        }
        if (unique.size() > WorkflowConstants.MAX_ADD_SIGNERS) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "单次最多加签 10 人");
        }
        return new ArrayList<>(unique);
    }

    /**
     * 读取任务快照的会签策略（缺省 ALL）。
     *
     * @param task 待办任务
     * @return 大写的策略文本
     */
    private static String policyOfTask(ProcessTaskPo task) {
        String policy = task.getSignPolicy();
        return policy == null || policy.isBlank() ? WorkflowConstants.POLICY_ALL : policy.trim().toUpperCase();
    }

    /**
     * 查询同一节点上的全部任务（优先按节点键，旧行无节点键时回落节点下标）。
     *
     * @param task 当前待办任务
     * @return 同节点任务列表
     */
    private List<ProcessTaskPo> listTasksOfNode(ProcessTaskPo task) {
        LambdaQueryWrapper<ProcessTaskPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTaskPo::getInstanceId, task.getInstanceId());
        if (task.getNodeKey() != null && !task.getNodeKey().isBlank()) {
            wrapper.eq(ProcessTaskPo::getNodeKey, task.getNodeKey());
        } else {
            wrapper.eq(ProcessTaskPo::getNodeIndex, task.getNodeIndex());
        }
        return processTaskMapper.selectList(wrapper);
    }
}
