package com.bluewhale.sts.auth.common.enums;

/**
 * 通用启用状态。
 *
 * <p>以**字符串**落库（{@code ENABLED} / {@code DISABLED}），不用 0/1：
 * 直接查库时一眼能看懂，不依赖"这个 1 到底是启用还是禁用"的心智记忆。
 * 本域所有状态列（租户 / 用户 / 组织 / 角色）统一用它。
 *
 * <p>{@link #parse(String)} 遇到无法识别的值返回 {@code null} 而**不兜底**：
 * 把非法值悄悄当成合法值，上游的拼写错误就永远暴露不出来 ——
 * 这条取舍与 {@code ClientLogLevel} 一致。
 */
public enum Status {

    ENABLED("ENABLED", "启用"),
    DISABLED("DISABLED", "停用");

    private final String wire;
    private final String label;

    Status(String wire, String label) {
        this.wire = wire;
        this.label = label;
    }

    public String wireName() {
        return wire;
    }

    public String getLabel() {
        return label;
    }

    public boolean isEnabled() {
        return this == ENABLED;
    }

    /** 大小写不敏感解析；无法识别返回 null */
    public static Status parse(String value) {
        if (value == null) {
            return null;
        }
        for (Status status : values()) {
            if (status.wire.equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }

}
