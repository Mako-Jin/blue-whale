package com.bluewhale.oa.approval.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批业务副作用聚合。
 *
 * <p>同一审批实例的同一副作用类型只允许一条记录，用于兜底保证扣额度、推 ERP、
 * 推档案等动作不重复执行。</p>
 *
 * @author BlueWhale
 */
@Data
public class ApprovalEffect {

    /** 主键 */
    private Long id;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 副作用类型 */
    private String effectType;

    /** 执行状态 */
    private ApprovalEffectStatus effectStatus;

    /** 执行参数快照（JSON） */
    private String payload;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 重试次数 */
    private int retryCount;

    /** 失败原因 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 是否已完成。
     *
     * @return 已完成返回 {@code true}
     */
    public boolean done() {
        return effectStatus == ApprovalEffectStatus.DONE;
    }

    /**
     * 标记执行完成；已完成时幂等返回。
     */
    public void markDone() {
        if (done()) {
            return;
        }
        effectStatus = ApprovalEffectStatus.DONE;
        executedAt = LocalDateTime.now();
        errorMsg = null;
    }

    /**
     * 标记执行失败。
     *
     * @param error 失败原因
     */
    public void markFailed(String error) {
        if (done()) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_EFFECT_CLOSED);
        }
        retryCount++;
        effectStatus = ApprovalEffectStatus.FAILED;
        errorMsg = error == null ? null : error.substring(0, Math.min(error.length(), 512));
    }
}
