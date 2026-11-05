package com.bluewhale.oa.hr.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工作日历保存命令（按日期幂等 upsert）。
 *
 * @author BlueWhale
 */
@Data
public class WorkCalendarSaveCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否工作日 */
    @NotNull(message = "是否工作日不能为空")
    private Boolean workday;

    /** 当日标准工时 */
    @DecimalMin(value = "0.0", message = "当日标准工时不能为负数")
    private BigDecimal workHours;

    /** 节假日名称 */
    @Size(max = 64, message = "节假日名称长度不能超过 64")
    private String holidayName;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
