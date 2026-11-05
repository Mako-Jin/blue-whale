package com.bluewhale.oa.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工作日历实体，对应表 {@code oa_work_calendar}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_work_calendar")
public class WorkCalendarEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 日历日期 */
    private LocalDate calendarDate;

    /** 是否工作日 */
    private Boolean workday;

    /** 当日标准工时 */
    private BigDecimal workHours;

    /** 节假日名称 */
    private String holidayName;

    /** 备注 */
    private String remark;
}
