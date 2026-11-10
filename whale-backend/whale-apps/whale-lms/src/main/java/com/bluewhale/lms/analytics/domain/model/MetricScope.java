package com.bluewhale.lms.analytics.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;

/**
 * 指标统计范围。
 *
 * @author BlueWhale
 */
public enum MetricScope {

    /** 全院汇总（当前唯一实际落库的范围） */
    ALL("全院"),

    /** 单课程（下钻位，M6 未落库） */
    COURSE("课程"),

    /** 单培训班（下钻位，M6 未落库） */
    CLASS("培训班");

    /** 中文标签 */
    private final String label;

    /**
     * 构造范围枚举。
     *
     * @param label 中文标签
     */
    MetricScope(String label) {
        this.label = label;
    }

    /**
     * 查询中文标签。
     *
     * @return 标签
     */
    public String getLabel() {
        return label;
    }

    /**
     * 解析范围类型。
     *
     * @param value 范围字符串
     * @return 范围枚举
     */
    public static MetricScope require(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        for (MetricScope scope : values()) {
            if (scope.name().equalsIgnoreCase(value.trim())) {
                return scope;
            }
        }
        throw LmsErrorCode.exception(LmsErrorCode.METRIC_INVALID, "统计范围不合法：" + value);
    }
}
