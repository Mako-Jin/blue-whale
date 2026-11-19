package com.bluewhale.message.enums;

import java.util.Locale;

/**
 * 消息优先级。
 *
 * <p>数值越大越优先，落库后用于异步投递队列与补偿任务排序，
 * 保证验证码、审批待办等时效性强的消息先于批量通知投递。</p>
 *
 * @author BlueWhale
 */
public enum MessagePriority {

    /** 高优先级：验证码、审批待办等时效性强的通知 */
    HIGH(30),

    /** 普通优先级（默认） */
    NORMAL(20),

    /** 低优先级：运营触达、批量通知 */
    LOW(10);

    /** 默认优先级 */
    public static final MessagePriority DEFAULT = NORMAL;

    /** 排序数值（越大越优先） */
    private final int code;

    /**
     * 构造优先级。
     *
     * @param code 排序数值
     */
    MessagePriority(int code) {
        this.code = code;
    }

    /**
     * 获取排序数值。
     *
     * @return 排序数值（越大越优先）
     */
    public int getCode() {
        return code;
    }

    /**
     * 按名称解析优先级，忽略大小写与首尾空白。
     *
     * @param name 优先级名称
     * @return 解析结果；空值或未知名称回退 {@link #NORMAL}
     */
    public static MessagePriority of(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }

    /**
     * 按数值解析优先级。
     *
     * @param code 排序数值
     * @return 解析结果；空值或未知数值回退 {@link #NORMAL}
     */
    public static MessagePriority of(Integer code) {
        if (code == null) {
            return DEFAULT;
        }
        for (MessagePriority priority : values()) {
            if (priority.code == code) {
                return priority;
            }
        }
        return DEFAULT;
    }
}
