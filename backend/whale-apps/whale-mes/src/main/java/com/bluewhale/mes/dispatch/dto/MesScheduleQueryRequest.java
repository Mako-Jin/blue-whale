package com.bluewhale.mes.dispatch.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 排程计划分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MesScheduleQueryRequest extends PageQuery {

    /** 排程日期 */
    private LocalDate planDate;

    /** 工单编码（模糊） */
    private String workOrderCode;

    /** 状态：DRAFT/LOCKED/CANCELLED */
    private String status;
}
