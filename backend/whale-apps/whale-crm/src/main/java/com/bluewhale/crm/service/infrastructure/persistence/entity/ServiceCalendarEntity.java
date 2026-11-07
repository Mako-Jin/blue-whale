package com.bluewhale.crm.service.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;

/**
 * 售后服务工作日历实体，对应表 {@code crm_service_calendar}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_service_calendar")
public class ServiceCalendarEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 日历编码 */
    private String calendarCode;

    /** 上班时间 */
    private LocalTime workdayStart;

    /** 下班时间 */
    private LocalTime workdayEnd;

    /** 午休分钟数 */
    private Integer breakMinutes;

    /** 工作日（ISO 1–7，逗号分隔） */
    private String weekdays;

    /** 是否启用 */
    private Boolean enabled;
}
