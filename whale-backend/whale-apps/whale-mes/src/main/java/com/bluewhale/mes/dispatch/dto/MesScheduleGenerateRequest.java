package com.bluewhale.mes.dispatch.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 有限产能排程生成请求。
 *
 * <p>按工作中心每班产能把待排任务分配到排程日期起的若干天；不指定工作中心时覆盖全部启用的工作中心。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesScheduleGenerateRequest {

    /** 排程起始日期，缺省为当天 */
    private LocalDate planDate;

    /** 排程天数，缺省 7 天 */
    @Min(value = 1, message = "{validation.mes.mes-schedule-generate.days-min}")
    @Max(value = 30, message = "{validation.mes.mes-schedule-generate.days-max}")
    private Integer days;

    /** 限定工作中心 ID 列表，缺省全部启用工作中心 */
    private List<Long> workCenterIds;

    /** 排程人 */
    @Size(max = 64, message = "{validation.mes.mes-schedule-generate.planned-by-size}")
    private String plannedBy;
}
