package com.bluewhale.tools.enums;

/**
 * 通用状态枚举占位
 *
 * @author BlueWhale
 */
public enum StatusEnum {

    ENABLED(1, "启用"),
    DISABLED(0, "禁用");

    private final Integer code;
    private final String desc;

    /**
     * 构造状态枚举项
     *
     * @param code 状态码
     * @param desc 状态描述
     */
    StatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取状态码
     *
     * @return 状态码
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 获取状态描述
     *
     * @return 状态描述
     */
    public String getDesc() {
        return desc;
    }
}
