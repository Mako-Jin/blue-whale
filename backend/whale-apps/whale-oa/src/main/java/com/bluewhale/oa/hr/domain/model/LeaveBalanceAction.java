package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 假期额度流水动作。
 *
 * <p>{@code days} 恒为正数，动作决定额度增减方向：
 * {@code INIT/ADJUST} 调整总额度，{@code FREEZE} 增加冻结，
 * {@code RELEASE} 减少冻结，{@code DEDUCT} 释放冻结并累加已使用。</p>
 *
 * @author BlueWhale
 */
public enum LeaveBalanceAction {

    /** 初始化额度 */
    INIT,

    /** 调整总额度 */
    ADJUST,

    /** 冻结（提交申请） */
    FREEZE,

    /** 释放冻结（驳回/撤销） */
    RELEASE,

    /** 扣减（审批通过） */
    DEDUCT;

    /**
     * 解析流水动作文本。
     *
     * @param value 动作文本
     * @return 流水动作
     */
    public static LeaveBalanceAction of(String value) {
        if (value == null) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_BALANCE_INVALID, "额度流水动作不能为空");
        }
        try {
            return LeaveBalanceAction.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_BALANCE_INVALID,
                    "额度流水动作仅支持 INIT / ADJUST / FREEZE / RELEASE / DEDUCT");
        }
    }
}
