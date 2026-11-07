package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户培育任务聚合。
 *
 * <p>仅 {@code OPEN} 允许改为 {@code DONE} 或 {@code CANCELLED}，已结束任务不可再变更。</p>
 *
 * @author BlueWhale
 */
@Data
public class NurtureTask {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 任务编号 */
    private String taskNo;

    /** CRM 客户 ID */
    private Long customerId;

    /** 客户名称快照 */
    private String customerName;

    /** 关联线索 ID */
    private Long leadId;

    /** 内容文档 ID */
    private Long contentDocId;

    /** 内容文档版本 */
    private Integer contentDocVersion;

    /** 触达渠道 */
    private String channel;

    /** 计划触达日期 */
    private LocalDate planDate;

    /** 任务状态 */
    private NurtureStatus status;

    /** 执行人 ID */
    private Long assigneeId;

    /** 执行人姓名 */
    private String assigneeName;

    /** 备注 */
    private String note;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 乐观锁版本 */
    private Integer lockVersion;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 变更任务状态。
     *
     * @param target 目标任务状态
     * @param at 变更时间
     */
    public void changeStatus(NurtureStatus target, LocalDateTime at) {
        if (target == null || target == NurtureStatus.OPEN) {
            throw CrmErrorCode.exception(CrmErrorCode.NURTURE_STATE_CONFLICT, "培育任务只能完成或取消");
        }
        if (status != NurtureStatus.OPEN) {
            throw CrmErrorCode.exception(CrmErrorCode.NURTURE_STATE_CONFLICT, "培育任务已结束，不允许重复变更");
        }
        this.status = target;
    }
}
