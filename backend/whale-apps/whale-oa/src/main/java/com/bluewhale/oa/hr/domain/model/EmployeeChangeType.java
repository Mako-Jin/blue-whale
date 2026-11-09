package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 人员异动类型。
 *
 * @author BlueWhale
 */
public enum EmployeeChangeType {

    /** 入职 */
    ONBOARD("入职"),
    /** 转正 */
    REGULAR("转正"),
    /** 调岗 */
    TRANSFER("调岗"),
    /** 离职 */
    LEAVE("离职"),
    /** 返聘 */
    REHIRE("返聘");

    private final String label;

    /**
     * 构造异动类型。
     *
     * @param label 中文名称
     */
    EmployeeChangeType(String label) {
        this.label = label;
    }

    /**
     * 解析异动类型。
     *
     * @param value 类型编码
     * @return 枚举
     */
    public static EmployeeChangeType of(String value) {
        if (value == null || value.isBlank()) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_INVALID, "异动类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_INVALID, "不支持的异动类型：" + value);
        }
    }

    /**
     * 获取中文名称。
     *
     * @return 中文名称
     */
    public String label() {
        return label;
    }

    /**
     * 是否离职。
     *
     * @return 离职返回 true
     */
    public boolean isLeave() {
        return this == LEAVE;
    }
}
