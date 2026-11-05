package com.bluewhale.mes.workforce.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 排班分配分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesShiftAssignmentQueryRequest {

    /** 排班日期 */
    private LocalDate shiftDate;

    /** 班次编码 */
    private String shiftCode;

    /** 班组 ID */
    private Long teamId;

    /** 工位 ID */
    private Long stationId;

    /** 员工 ID */
    private Long employeeId;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
