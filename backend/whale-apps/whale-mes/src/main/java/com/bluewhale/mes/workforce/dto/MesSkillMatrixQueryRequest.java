package com.bluewhale.mes.workforce.dto;

import lombok.Data;

/**
 * 技能矩阵分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesSkillMatrixQueryRequest {

    /** 员工 ID */
    private Long employeeId;

    /** 技能编码（模糊） */
    private String skillCode;

    /** 技能等级 */
    private String skillLevel;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
