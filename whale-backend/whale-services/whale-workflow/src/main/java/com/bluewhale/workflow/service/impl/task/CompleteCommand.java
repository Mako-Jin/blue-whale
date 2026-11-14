package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.request.ApprovalRequest;
import com.bluewhale.workflow.request.TaskCompleteRequest;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.support.WorkflowConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 内网系统完成任务命令：供第三方签章回调等异步场景推进流程。
 *
 * <p>按任务状态做幂等：已同意且再次 APPROVE、已驳回且再次 REJECT 均直接返回；
 * 其余非待办状态一律拒绝。操作来源写入 {@code operator_id/operator_type/operator_source}，
 * 复用既有 {@link ApproveCommand} / {@link RejectCommand} 保持流程推进语义一致。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompleteCommand {

    /** 动作：同意 */
    private static final String ACTION_APPROVE = "APPROVE";

    /** 动作：驳回 */
    private static final String ACTION_REJECT = "REJECT";

    /** 操作类型：系统/回调 */
    private static final String OPERATOR_SYSTEM = "SYSTEM";

    /** 操作类型：人工 */
    private static final String OPERATOR_USER = "USER";

    /** 默认操作来源 */
    private static final String DEFAULT_SOURCE = "INTERNAL";

    private final ProcessTaskMapper processTaskMapper;

    private final ApproveCommand approveCommand;

    private final RejectCommand rejectCommand;

    /**
     * 内网完成任务并推进流程（幂等）。
     *
     * @param id      任务ID
     * @param request 完成请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, TaskCompleteRequest request) {
        ProcessTaskPo task = processTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        TaskCompleteRequest command = request == null ? new TaskCompleteRequest() : request;
        String action = resolveAction(command.getAction());
        if (isIdempotent(task.getStatus(), action)) {
            log.info("内网完成任务幂等返回: taskId={}, action={}, eventId={}", id, action, command.getEventId());
            return;
        }
        requirePending(task.getStatus());
        markOperator(task, command);
        if (ACTION_REJECT.equals(action)) {
            rejectCommand.reject(id, command.getComment());
            return;
        }
        approveCommand.approve(id, buildApproval(command));
    }

    /**
     * 解析并校验动作。
     */
    private String resolveAction(String value) {
        String action = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : ACTION_APPROVE;
        if (!ACTION_APPROVE.equals(action) && !ACTION_REJECT.equals(action)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的任务动作: " + action);
        }
        return action;
    }

    /**
     * 判断重复调用是否可直接返回（同一动作已完成）。
     */
    private boolean isIdempotent(Integer status, String action) {
        if (status == null) {
            return false;
        }
        if (ACTION_APPROVE.equals(action)) {
            return status == WorkflowConstants.TASK_APPROVED;
        }
        return status == WorkflowConstants.TASK_REJECTED;
    }

    /**
     * 校验任务仍为待办状态。
     */
    private void requirePending(Integer status) {
        if (status == null || status != WorkflowConstants.TASK_PENDING) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "任务已处理");
        }
    }

    /**
     * 记录系统操作来源。
     */
    private void markOperator(ProcessTaskPo task, TaskCompleteRequest command) {
        task.setOperatorId(parseOperatorId(command.getOperatorId()));
        task.setOperatorType(resolveOperatorType(command.getOperatorType()));
        task.setOperatorSource(StringUtils.hasText(command.getSource()) ? command.getSource() : DEFAULT_SOURCE);
        processTaskMapper.updateById(task);
    }

    /**
     * 组装审批命令参数。
     */
    private ApprovalRequest buildApproval(TaskCompleteRequest command) {
        ApprovalRequest approval = new ApprovalRequest();
        approval.setComment(command.getComment());
        approval.setVariables(command.getVariables());
        return approval;
    }

    /**
     * 解析操作类型：仅接受 USER，其余按系统回调。
     *
     * @param value 操作类型文本
     * @return 操作类型
     */
    private String resolveOperatorType(String value) {
        return StringUtils.hasText(value) && OPERATOR_USER.equalsIgnoreCase(value.trim())
                ? OPERATOR_USER : OPERATOR_SYSTEM;
    }

    /**
     * 解析操作人ID。
     *
     * @param value 用户ID文本
     * @return 用户ID；非法或为空返回 {@code null}
     */
    private Long parseOperatorId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
