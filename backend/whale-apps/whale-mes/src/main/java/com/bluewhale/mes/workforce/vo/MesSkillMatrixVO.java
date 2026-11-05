package com.bluewhale.mes.workforce.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工技能矩阵视图
 *
 * @author BlueWhale
 */
@Data
public class MesSkillMatrixVO {

    /** 技能记录 ID */
    private Long id;

    /** 员工 ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 技能编码 */
    private String skillCode;

    /** 技能名称 */
    private String skillName;

    /** 技能等级 */
    private String skillLevel;

    /** 认证日期 */
    private LocalDate certifiedAt;

    /** 失效日期 */
    private LocalDate expiryAt;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
