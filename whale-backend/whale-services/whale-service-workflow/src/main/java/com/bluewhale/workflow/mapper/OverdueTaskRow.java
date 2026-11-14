package com.bluewhale.workflow.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 超时待办的轻量查询行（仅扫描所需字段，避免整行读取与多余列）。
 *
 * <p>需要 {@code tenantId}：超时扫描在无租户上下文的后台线程执行，
 * 后续站内信提醒必须显式携带租户，不能依赖请求线程的 {@code TenantContext}。</p>
 *
 * @author BlueWhale
 */
@Data
public class OverdueTaskRow {

    /** 任务ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 所属流程实例ID */
    private Long instanceId;

    /** 节点名称 */
    private String nodeName;

    /** 审批人账号 */
    private String assignee;

    /** 到期时间 */
    private LocalDateTime dueAt;
}
