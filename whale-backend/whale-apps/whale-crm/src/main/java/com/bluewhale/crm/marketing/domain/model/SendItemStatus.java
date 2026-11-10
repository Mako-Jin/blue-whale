package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 触达明细状态。
 *
 * <p>分为两段：<b>提交态</b> {@code PENDING / SENT / SKIPPED / FAILED} 由发送动作写入；
 * <b>回执态</b> {@code DELIVERED / READ / BOUNCED} 由 whale-message 的渠道回执驱动。
 * 回执态只允许向前推进，乱序回执不会把「已读」打回「已送达」。</p>
 *
 * @author BlueWhale
 */
public enum SendItemStatus {

    /** 待发送 */
    PENDING,

    /** 已发送（已提交渠道，尚未确认送达） */
    SENT,

    /** 渠道已确认送达（回执态） */
    DELIVERED,

    /** 接收人已读（回执态） */
    READ,

    /** 渠道退信（回执态，终态） */
    BOUNCED,

    /** 已跳过（重复 / 退订 / 黑名单） */
    SKIPPED,

    /** 发送失败 */
    FAILED;

    /**
     * 解析明细状态。
     *
     * @param value 文本
     * @return 明细状态
     */
    public static SendItemStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "触达明细状态不能为空");
        }
        for (SendItemStatus status : values()) {
            if (status.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
                return status;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "触达明细状态非法：" + value);
    }

    /**
     * 解析渠道回执状态（仅接受回执态）。
     *
     * @param value 文本
     * @return 回执态
     */
    public static SendItemStatus fromReceipt(String value) {
        SendItemStatus status = from(value);
        if (!status.isReceiptStatus()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID,
                    "回执状态非法（仅支持 DELIVERED / READ / BOUNCED）：" + value);
        }
        return status;
    }

    /**
     * 是否为回执态。
     *
     * @return 回执态返回 true
     */
    public boolean isReceiptStatus() {
        return this == DELIVERED || this == READ || this == BOUNCED;
    }

    /**
     * 判断是否已触达（用于「同一活动同一联系人同一渠道只发一次」的去重）。
     *
     * @return 已提交或已送达/已读返回 true
     */
    public boolean isReached() {
        return this == SENT || this == DELIVERED || this == READ;
    }

    /**
     * 判断是否允许由当前状态推进到目标回执态。
     *
     * @param target 目标回执态
     * @return 允许推进返回 true
     */
    public boolean canAdvanceTo(SendItemStatus target) {
        if (target == null || !target.isReceiptStatus()) {
            return false;
        }
        if (this == BOUNCED) {
            return false;
        }
        if (this != SENT && this != DELIVERED && this != READ) {
            return false;
        }
        if (target == BOUNCED) {
            return this != BOUNCED;
        }
        return target.ordinal() > this.ordinal();
    }
}
