package com.bluewhale.crm.commerce.domain.model;

import java.time.LocalDate;

/**
 * 回款催收优先级。
 *
 * <p>默认口径：逾期超过 30 天 HIGH、超过 7 天 MEDIUM、其余 LOW；
 * 计划行允许人工覆盖。</p>
 *
 * @author BlueWhale
 */
public enum CollectionPriority {

    /** 高优先级 */
    HIGH,

    /** 中优先级 */
    MEDIUM,

    /** 低优先级 */
    LOW;

    /**
     * 解析优先级：人工覆盖优先，其次按计划回款日期计算。
     *
     * @param override 人工覆盖值（可为空）
     * @param plannedDate 计划回款日期
     * @return 优先级
     */
    public static CollectionPriority resolve(String override, LocalDate plannedDate) {
        CollectionPriority manual = from(override);
        if (manual != null) {
            return manual;
        }
        if (plannedDate == null) {
            return LOW;
        }
        long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(plannedDate, LocalDate.now());
        if (overdueDays > 30) {
            return HIGH;
        }
        if (overdueDays > 7) {
            return MEDIUM;
        }
        return LOW;
    }

    /**
     * 安全解析枚举值。
     *
     * @param value 文本
     * @return 枚举；无法识别返回 {@code null}
     */
    public static CollectionPriority from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (CollectionPriority priority : values()) {
            if (priority.name().equalsIgnoreCase(value.trim())) {
                return priority;
            }
        }
        return null;
    }
}
