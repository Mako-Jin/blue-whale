package com.bluewhale.mes.workforce.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 工时记录分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesWorkHourQueryRequest {

    /** 员工 ID */
    private Long employeeId;

    /** 工作日期 */
    private LocalDate workDate;

    /** 任务 ID */
    private Long taskId;

    /** 来源 */
    private String source;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
