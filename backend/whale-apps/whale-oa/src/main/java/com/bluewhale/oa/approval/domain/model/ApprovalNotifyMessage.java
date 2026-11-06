package com.bluewhale.oa.approval.domain.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 审批通知消息。
 *
 * <p>由审批上下文组装后交给消息服务投递；模板编码对齐 {@code whale-message} 中
 * {@code OA_APPROVAL_*} 预置模板，模板变量由 {@code payload} 提供。</p>
 *
 * @author BlueWhale
 */
@Data
public class ApprovalNotifyMessage {

    /** 业务类型 */
    private String bizType;

    /** 业务标识 */
    private String bizId;

    /** 幂等键 */
    private String idempotencyKey;

    /** 模板编码 */
    private String templateCode;

    /** 消息标题（模板渲染失败时的兜底） */
    private String title;

    /** 消息正文（模板渲染失败时的兜底） */
    private String content;

    /** 模板变量 */
    private Map<String, Object> payload;

    /** 接收人用户ID */
    private List<Long> userIds;
}
