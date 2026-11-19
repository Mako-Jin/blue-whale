package com.bluewhale.workflow.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 内网系统完成任务请求（第三方签章回调等异步场景）。
 *
 * <p>与人工审批的区别：由服务间内网通道调用，操作来源固定记录为系统/回调，
 * 通过 {@code action + taskId} 保证幂等，重复调用不会重复推进流程。</p>
 *
 * @author BlueWhale
 */
@Data
public class TaskCompleteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 实际完成人用户ID（签署人；可为空，非空时必须为数字） */
    @Pattern(regexp = "\\d{1,19}", message = "{validation.workflow.task.operator-id-pattern}")
    private String operatorId;

    /** 动作：APPROVE 同意 / REJECT 驳回，默认 APPROVE */
    @Size(max = 32, message = "{validation.workflow.task.action-size}")
    private String action;

    /** 处理意见（最长 255） */
    @Size(max = 255, message = "{validation.workflow.task.handle-comment-size}")
    private String comment;

    /** 追加的流程变量 */
    private Map<String, Object> variables;

    /** 幂等事件ID（回调事件ID，便于日志追踪，最长 64） */
    @Size(max = 64, message = "{validation.workflow.task.event-id-size}")
    private String eventId;

    /** 操作来源，如 ESIGN_CALLBACK（最长 32） */
    @Size(max = 32, message = "{validation.workflow.task.operation-source-size}")
    private String source;

    /** 操作类型：USER 人工 / SYSTEM 系统回调，默认 SYSTEM（最长 32） */
    @Size(max = 32, message = "{validation.workflow.task.operation-type-size}")
    private String operatorType;
}
