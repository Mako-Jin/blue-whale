package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 生产工作中心。
 *
 * <p>不变量：编码与名称非空；日产能为正；效率 0–100；工作日为 1–7 的非空集合。</p>
 *
 * @author BlueWhale
 */
@Data
public class WorkCenter {

    /** 比例小数位 */
    private static final int RATE_SCALE = 4;

    /** 主键 */
    private Long id;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 所属部门 */
    private String department;

    /** 日产能工时（小时） */
    private BigDecimal dailyCapacityHours;

    /** 效率百分比（0–100） */
    private BigDecimal efficiencyRate;

    /** 工作日（ISO 1–7，逗号分隔） */
    private String workDays;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 校验主数据。
     */
    public void validate() {
        if (code == null || code.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_INVALID, "工作中心编码不能为空");
        }
        if (name == null || name.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_INVALID, "工作中心名称不能为空");
        }
        if (dailyCapacityHours == null || dailyCapacityHours.signum() <= 0) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_INVALID, "工作中心日产能必须大于 0");
        }
        if (efficiencyRate == null
                || efficiencyRate.signum() < 0
                || efficiencyRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_INVALID, "工作中心效率必须在 0–100 之间");
        }
        Set<Integer> days = workDaySet();
        if (days.isEmpty() || days.stream().anyMatch(day -> day < 1 || day > 7)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_INVALID,
                    "工作中心工作日必须为 1–7 的非空集合");
        }
    }

    /**
     * 判断指定日期是否工作日。
     *
     * @param date 日期
     * @return 工作日返回 true
     */
    public boolean worksOn(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return workDaySet().contains(dayOfWeek.getValue());
    }

    /**
     * 计算指定日期的可用产能工时。
     *
     * @param date 日期
     * @return 产能工时（非工作日为 0）
     */
    public BigDecimal capacityOn(LocalDate date) {
        if (!worksOn(date)) {
            return BigDecimal.ZERO;
        }
        return dailyCapacityHours.multiply(efficiencyRate)
                .divide(BigDecimal.valueOf(100), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 解析工作日集合。
     *
     * @return 工作日 ISO 序号集合
     */
    private Set<Integer> workDaySet() {
        if (workDays == null || workDays.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(workDays.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        throw ErpErrorCode.exception(ErpErrorCode.MFG_INVALID,
                                "工作中心工作日格式非法：" + workDays);
                    }
                })
                .collect(Collectors.toSet());
    }
}
