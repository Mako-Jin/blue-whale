package com.bluewhale.mes.workforce.dto;

import lombok.Data;

/**
 * 班组成员分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesTeamMemberQueryRequest {

    /** 班组 ID */
    private Long teamId;

    /** 员工 ID */
    private Long employeeId;

    /** 员工姓名（模糊） */
    private String employeeName;

    /** 状态 */
    private String status;

    /** 是否主操作员：0 否 / 1 是 */
    private Integer primaryOperator;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
