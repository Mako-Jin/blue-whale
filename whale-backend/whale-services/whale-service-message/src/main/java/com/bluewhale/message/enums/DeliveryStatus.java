package com.bluewhale.message.enums;

import java.util.Locale;

/**
 * 单条投递结果状态。
 *
 * <p>状态分两段：<b>提交态</b>由本服务的渠道适配器在发送时写入
 * （{@code SENT / FAILED / SKIPPED / MOCKED}）；<b>回执态</b>由渠道回执上报写入
 * （{@code DELIVERED / READ / BOUNCED}）。回执态只允许向前推进，避免乱序回执把
 * "已读"回退成"已送达"。</p>
 *
 * @author BlueWhale
 */
public enum DeliveryStatus {

    /** 已提交给渠道 */
    SENT(1),

    /** 渠道已确认送达（回执态） */
    DELIVERED(2),

    /** 接收人已阅读（回执态，仅可回传阅读态的渠道） */
    READ(3),

    /** 渠道退信（回执态，终态：地址无效、拒收等） */
    BOUNCED(4),

    /** 渠道调用失败 */
    FAILED(0),

    /** 渠道未启用/缺少接收信息，主动跳过 */
    SKIPPED(0),

    /** mock 模式：未发起真实请求 */
    MOCKED(0);

    /** 回执推进阶梯：数值越大表示越靠后，仅允许递增 */
    private final int progress;

    /**
     * 构造状态。
     *
     * @param progress 回执推进阶梯值
     */
    DeliveryStatus(int progress) {
        this.progress = progress;
    }

    /**
     * 回执推进阶梯值。
     *
     * @return 阶梯值，提交态与失败态为 0
     */
    public int getProgress() {
        return progress;
    }

    /**
     * 判断当前状态是否允许推进到目标回执态。
     *
     * <p>规则：只有 {@code SENT / DELIVERED / READ} 可以承接回执；{@code DELIVERED}、{@code READ}
     * 必须严格向前推进（同一状态重复上报不算推进，由幂等层过滤）；{@code BOUNCED} 为终态，
     * 可从任一已提交状态进入，但不能再被其它回执覆盖。</p>
     *
     * @param target 目标回执态
     * @return 允许推进时返回 {@code true}
     */
    public boolean canAdvanceTo(DeliveryStatus target) {
        if (target == null) {
            return false;
        }
        if (target == BOUNCED) {
            return this == SENT || this == DELIVERED || this == READ;
        }
        if (this == BOUNCED) {
            return false;
        }
        if (this != SENT && this != DELIVERED && this != READ) {
            return false;
        }
        return target.progress > this.progress;
    }

    /**
     * 解析状态文本（大小写不敏感）。
     *
     * @param value 状态文本
     * @return 状态；无法识别时返回 {@code null}
     */
    public static DeliveryStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (DeliveryStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 是否为回执态。
     *
     * @return 回执态返回 {@code true}
     */
    public boolean isReceiptStatus() {
        return this == DELIVERED || this == READ || this == BOUNCED;
    }
}
