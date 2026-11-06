package com.bluewhale.workflow.dto;

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

    /** 实际完成人用户ID（签署人；可为空） */
    private String operatorId;

    /** 动作：APPROVE 同意 / REJECT 驳回，默认 APPROVE */
    private String action;

    /** 处理意见 */
    private String comment;

    /** 追加的流程变量 */
    private Map<String, Object> variables;

    /** 幂等事件ID（回调事件ID，便于日志追踪） */
    private String eventId;

    /** 操作来源，如 ESIGN_CALLBACK */
    private String source;

    /** 操作类型：USER 人工 / SYSTEM 系统回调，默认 SYSTEM */
    private String operatorType;
}
