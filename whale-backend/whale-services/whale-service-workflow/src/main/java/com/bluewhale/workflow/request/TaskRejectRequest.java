package com.bluewhale.workflow.request;

import com.bluewhale.workflow.support.RejectTarget;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 驳回（退回）请求：指定退回目标与处理节点。
 *
 * <p>驳回不再终止实例：实例保持运行中，其余待办取消，目标处理人收到新待办。
 * 会签节点按策略自动决定退回目标，此处的 {@code target} 会被忽略。</p>
 *
 * @author BlueWhale
 */
@Data
public class TaskRejectRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审批意见（可选，最长 255） */
    @Size(max = 255, message = "审批意见长度不能超过 255")
    private String comment;

    /** 退回目标（必填） */
    @NotNull(message = "请选择驳回目标")
    private RejectTarget target;

    /** 指定节点键（{@code target=SPECIFIED} 时必填，须为上游审批节点） */
    @Size(max = 64, message = "节点键长度不能超过 64")
    private String targetNodeKey;
}
