package com.bluewhale.crm.analytics.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 行动计划项。
 *
 * <p>不变量：仅 {@link ActionItemStatus#OPEN} 可关闭；手工关闭必须填写原因。
 * 同一租户内 {@code dedupKey} 唯一；已关闭的行动项不会再次生成。</p>
 *
 * @author BlueWhale
 */
@Data
public class ActionItem {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 来源规则编码 */
    private String ruleCode;

    /** 关联对象类型 */
    private ActionType refType;

    /** 关联对象 ID */
    private Long refId;

    /** 客户 ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 标题 */
    private String title;

    /** 行动建议 */
    private String suggestion;

    /** 优先级 */
    private String priority;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 建议完成日期 */
    private LocalDate dueDate;

    /** 状态 */
    private ActionItemStatus status;

    /** 关闭原因 */
    private String closeReason;

    /** 关闭人 */
    private Long closedBy;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 去重键 */
    private String dedupKey;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 关闭行动项。
     *
     * @param reason 关闭原因
     * @param closedBy 关闭人
     * @param closedAt 关闭时间
     */
    public void close(String reason, Long closedBy, LocalDateTime closedAt) {
        if (status != ActionItemStatus.OPEN) {
            throw CrmErrorCode.exception(CrmErrorCode.ACTION_ITEM_STATE_CONFLICT);
        }
        if (reason == null || reason.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "关闭行动项必须填写原因");
        }
        this.status = ActionItemStatus.CLOSED;
        this.closeReason = reason.trim();
        this.closedBy = closedBy;
        this.closedAt = closedAt;
    }
}
