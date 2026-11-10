package com.bluewhale.crm.service.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 售后服务工单聚合根。
 *
 * <p>状态机 {@code NEW → ASSIGNED → PROCESSING ⇄ WAITING_CUSTOMER → RESOLVED → CLOSED}，
 * 重开回到 {@code REOPENED}；未关闭前允许取消。SLA 截止时间由应用层按优先级策略与工作日历计算后写入，
 * 暂停 / 恢复按实际暂停时长顺延截止时间。</p>
 *
 * @author BlueWhale
 */
@Data
public class Ticket {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 工单号 */
    private String ticketNo;

    /** CRM 客户 ID */
    private Long customerId;

    /** 客户编码快照 */
    private String customerCode;

    /** 客户名称快照 */
    private String customerName;

    /** 联系人 ID */
    private Long contactId;

    /** 联系人姓名快照 */
    private String contactName;

    /** 合同台账 ID */
    private Long contractId;

    /** 商务订单 ID */
    private Long orderId;

    /** ERP 销售订单号快照 */
    private String erpOrderNo;

    /** 产品编码 */
    private String productCode;

    /** 序列号 / 设备号 */
    private String serialNo;

    /** 问题分类 */
    private String category;

    /** 优先级 */
    private TicketPriority priority;

    /** 工单标题 */
    private String title;

    /** 问题描述 */
    private String description;

    /** 状态 */
    private TicketStatus status;

    /** 处理人 ID */
    private Long assigneeId;

    /** 处理人姓名快照 */
    private String assigneeName;

    /** 负责人 ID */
    private Long ownerId;

    /** 负责部门 ID */
    private Long deptId;

    /** 响应截止时间 */
    private LocalDateTime responseDeadline;

    /** 解决截止时间 */
    private LocalDateTime resolveDeadline;

    /** 首次响应时间 */
    private LocalDateTime firstRespondedAt;

    /** 暂停开始时间 */
    private LocalDateTime pausedAt;

    /** 累计暂停秒数 */
    private Long pausedSeconds;

    /** 解决时间 */
    private LocalDateTime resolvedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 解决方案 */
    private String solution;

    /** 重开次数 */
    private Integer reopenCount;

    /** 乐观锁版本 */
    private Integer lockVersion;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 写入 SLA 截止时间。
     *
     * @param response 响应截止时间
     * @param resolve 解决截止时间
     */
    public void applyDeadlines(LocalDateTime response, LocalDateTime resolve) {
        this.responseDeadline = response;
        this.resolveDeadline = resolve;
    }

    /**
     * 分派处理人。
     *
     * @param newAssigneeId 处理人 ID
     * @param newAssigneeName 处理人姓名
     * @param at 分派时间
     */
    public void assign(Long newAssigneeId, String newAssigneeName, LocalDateTime at) {
        if (newAssigneeId == null) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_ASSIGNEE_INVALID);
        }
        if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED
                || status == TicketStatus.CANCELLED) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_STATE_CONFLICT, "仅未完成的工单允许分派");
        }
        this.assigneeId = newAssigneeId;
        this.assigneeName = newAssigneeName == null ? null : newAssigneeName.trim();
        if (status == TicketStatus.NEW || status == TicketStatus.REOPENED) {
            this.status = TicketStatus.ASSIGNED;
        }
    }

    /**
     * 记录处理进展。
     *
     * @param content 处理内容
     * @param at 处理时间
     */
    public void process(String content, LocalDateTime at) {
        requireOpen();
        requireContent(content, "处理内容不能为空");
        if (status == TicketStatus.WAITING_CUSTOMER) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_STATE_CONFLICT, "等待客户期间请先恢复工单");
        }
        if (status == TicketStatus.RESOLVED) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_STATE_CONFLICT, "已解决工单请先重开再处理");
        }
        this.status = TicketStatus.PROCESSING;
        markFirstResponse(at);
    }

    /**
     * 追加备注。
     *
     * @param content 备注内容
     * @param at 记录时间
     */
    public void comment(String content, LocalDateTime at) {
        requireOpen();
        requireContent(content, "备注内容不能为空");
        markFirstResponse(at);
    }

    /**
     * 暂停工单（等待客户回复）。
     *
     * @param reason 暂停原因
     * @param at 暂停时间
     */
    public void pause(String reason, LocalDateTime at) {
        requireOpen();
        requireContent(reason, "暂停原因不能为空");
        if (status == TicketStatus.WAITING_CUSTOMER) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_STATE_CONFLICT, "工单已处于等待客户状态");
        }
        if (status == TicketStatus.RESOLVED) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_STATE_CONFLICT, "已解决工单不允许暂停");
        }
        this.status = TicketStatus.WAITING_CUSTOMER;
        this.pausedAt = at;
    }

    /**
     * 恢复工单。
     *
     * @param at 恢复时间
     * @return 本次暂停秒数
     */
    public long resume(LocalDateTime at) {
        if (status != TicketStatus.WAITING_CUSTOMER || pausedAt == null) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_STATE_CONFLICT, "仅等待客户的工单允许恢复");
        }
        long seconds = Math.max(0L, Duration.between(pausedAt, at).getSeconds());
        this.status = TicketStatus.PROCESSING;
        this.pausedAt = null;
        this.pausedSeconds = (pausedSeconds == null ? 0L : pausedSeconds) + seconds;
        return seconds;
    }

    /**
     * 升一级优先级。
     *
     * @return 升级后的优先级
     */
    public TicketPriority escalate() {
        requireOpen();
        this.priority = priority == null ? TicketPriority.P3 : priority.escalate();
        return this.priority;
    }

    /**
     * 标记已解决。
     *
     * @param content 解决方案
     * @param at 解决时间
     */
    public void resolve(String content, LocalDateTime at) {
        requireOpen();
        requireContent(content, "解决方案不能为空");
        if (status == TicketStatus.RESOLVED) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_STATE_CONFLICT, "工单已解决");
        }
        this.solution = content.trim();
        this.status = TicketStatus.RESOLVED;
        this.resolvedAt = at;
    }

    /**
     * 关闭工单。
     *
     * @param content 最终解决方案
     * @param at 关闭时间
     */
    public void close(String content, LocalDateTime at) {
        if (status != TicketStatus.RESOLVED) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_STATE_CONFLICT, "仅已解决工单允许关闭");
        }
        requireContent(content, "关闭工单必须填写解决方案");
        this.solution = content.trim();
        this.status = TicketStatus.CLOSED;
        this.closedAt = at;
    }

    /**
     * 重开工单。
     *
     * @param reason 重开原因
     * @param at 重开时间
     */
    public void reopen(String reason, LocalDateTime at) {
        if (status != TicketStatus.RESOLVED && status != TicketStatus.CLOSED) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_STATE_CONFLICT, "仅已解决或已关闭工单允许重开");
        }
        requireContent(reason, "重开原因不能为空");
        this.status = TicketStatus.REOPENED;
        this.reopenCount = (reopenCount == null ? 0 : reopenCount) + 1;
        this.resolvedAt = null;
        this.closedAt = null;
    }

    /**
     * 取消工单。
     *
     * @param reason 取消原因
     * @param at 取消时间
     */
    public void cancel(String reason, LocalDateTime at) {
        if (status == TicketStatus.CLOSED) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_STATE_CONFLICT, "已关闭工单不允许取消");
        }
        requireContent(reason, "取消原因不能为空");
        this.status = TicketStatus.CANCELLED;
        this.closedAt = at;
    }

    /**
     * 顺延 SLA 截止时间（暂停恢复后按实际暂停时长补偿）。
     *
     * @param seconds 顺延秒数
     */
    public void shiftDeadlines(long seconds) {
        if (seconds <= 0) {
            return;
        }
        if (responseDeadline != null) {
            this.responseDeadline = responseDeadline.plusSeconds(seconds);
        }
        if (resolveDeadline != null) {
            this.resolveDeadline = resolveDeadline.plusSeconds(seconds);
        }
    }

    /**
     * 标记首次响应。
     *
     * @param at 响应时间
     */
    private void markFirstResponse(LocalDateTime at) {
        if (firstRespondedAt == null) {
            this.firstRespondedAt = at;
        }
    }

    /**
     * 校验工单处于可操作状态。
     */
    private void requireOpen() {
        if (status == TicketStatus.CLOSED || status == TicketStatus.CANCELLED) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_STATE_CONFLICT, "工单已结束，不允许该操作");
        }
    }

    /**
     * 校验文本必填。
     *
     * @param content 文本
     * @param message 提示语
     */
    private void requireContent(String content, String message) {
        if (content == null || content.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, message);
        }
    }
}
