package com.bluewhale.party.integration.domain.model;

/**
 * 通知投递状态。
 *
 * @author BlueWhale
 */
public enum NotifyStatus {

    /** 已投递 */
    SENT("已发送"),

    /** 投递失败（下一轮可重试） */
    FAILED("发送失败");

    /** 中文标签 */
    private final String label;

    /**
     * 构造投递状态。
     *
     * @param label 中文标签
     */
    NotifyStatus(String label) {
        this.label = label;
    }

    /**
     * 中文标签。
     *
     * @return 标签
     */
    public String getLabel() {
        return label;
    }

    /**
     * 是否已投递。
     *
     * @return 已投递返回 true
     */
    public boolean isSent() {
        return this == SENT;
    }

    /**
     * 解析状态（空值返回 null）。
     *
     * @param value 原始值
     * @return 状态
     */
    public static NotifyStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (NotifyStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }
}
