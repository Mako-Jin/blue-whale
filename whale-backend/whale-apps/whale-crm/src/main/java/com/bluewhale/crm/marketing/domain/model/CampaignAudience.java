package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动人群快照聚合。
 *
 * <p>快照锁定后条件与成员不可再变更，二次锁定一律拒绝。</p>
 *
 * @author BlueWhale
 */
@Data
public class CampaignAudience {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 营销活动 ID */
    private Long campaignId;

    /** 快照编号 */
    private String snapshotNo;

    /** 人群条件 JSON */
    private String criteriaJson;

    /** 成员数量 */
    private Integer memberCount;

    /** 是否已锁定 */
    private Boolean locked;

    /** 锁定时间 */
    private LocalDateTime lockedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 锁定人群快照。
     *
     * @param at 锁定时间
     */
    public void lock(LocalDateTime at) {
        if (Boolean.TRUE.equals(locked)) {
            throw CrmErrorCode.exception(CrmErrorCode.AUDIENCE_INVALID, "人群快照已锁定，不可重复锁定");
        }
        this.locked = Boolean.TRUE;
        this.lockedAt = at;
    }

    /**
     * 校验快照可编辑（未锁定）。
     */
    public void ensureEditable() {
        if (Boolean.TRUE.equals(locked)) {
            throw CrmErrorCode.exception(CrmErrorCode.AUDIENCE_INVALID, "人群快照已锁定，不可变更");
        }
    }
}
