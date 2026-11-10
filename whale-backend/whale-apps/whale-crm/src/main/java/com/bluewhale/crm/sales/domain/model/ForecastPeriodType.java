package com.bluewhale.crm.sales.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 销售预测 / 目标周期类型：月与季。
 *
 * <p>月周期格式 {@code yyyy-MM}（如 {@code 2026-11}）；
 * 季周期格式 {@code yyyy-Qn}（如 {@code 2026-Q4}）。</p>
 *
 * @author BlueWhale
 */
public enum ForecastPeriodType {

    /** 月 */
    MONTH("^\\d{4}-(0[1-9]|1[0-2])$"),

    /** 季 */
    QUARTER("^\\d{4}-Q[1-4]$");

    /** 周期格式 */
    private final Pattern pattern;

    /**
     * 构造周期类型。
     *
     * @param regex 周期格式正则
     */
    ForecastPeriodType(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    /**
     * 解析周期类型，空白默认按月。
     *
     * @param code 周期类型码
     * @return 周期类型
     */
    public static ForecastPeriodType from(String code) {
        if (code == null || code.isBlank()) {
            return MONTH;
        }
        String text = code.trim().toUpperCase(Locale.ROOT);
        return switch (text) {
            case "MONTH" -> MONTH;
            case "QUARTER" -> QUARTER;
            default -> throw CrmErrorCode.exception(CrmErrorCode.FORECAST_PERIOD_INVALID);
        };
    }

    /**
     * 校验并规范化周期文本。
     *
     * @param period 周期文本
     * @return 规范化后的周期文本
     */
    public String normalize(String period) {
        if (period == null || period.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.FORECAST_PERIOD_INVALID);
        }
        String text = period.trim().toUpperCase(Locale.ROOT);
        if (!pattern.matcher(text).matches()) {
            throw CrmErrorCode.exception(CrmErrorCode.FORECAST_PERIOD_INVALID);
        }
        return text;
    }

    /**
     * 计算周期起始日期。
     *
     * @param period 已规范化的周期文本
     * @return 起始日期
     */
    public LocalDate startDate(String period) {
        int year = Integer.parseInt(period.substring(0, 4));
        if (this == MONTH) {
            return LocalDate.of(year, Integer.parseInt(period.substring(5, 7)), 1);
        }
        int quarter = Integer.parseInt(period.substring(6, 7));
        return LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
    }

    /**
     * 计算周期结束日期。
     *
     * @param period 已规范化的周期文本
     * @return 结束日期
     */
    public LocalDate endDate(String period) {
        return startDate(period).plusMonths(this == MONTH ? 1 : 3).minusDays(1);
    }
}
