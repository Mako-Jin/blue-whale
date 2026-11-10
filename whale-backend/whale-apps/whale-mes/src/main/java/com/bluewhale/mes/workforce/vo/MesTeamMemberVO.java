package com.bluewhale.mes.workforce.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 班组成员视图
 *
 * @author BlueWhale
 */
@Data
public class MesTeamMemberVO {

    /** 成员 ID */
    private Long id;

    /** 班组 ID */
    private Long teamId;

    /** 班组编码 */
    private String teamCode;

    /** 员工 ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 是否主操作员：0 否 / 1 是 */
    private Integer primaryOperator;

    /** 技能等级 */
    private String skillLevel;

    /** 入组日期 */
    private LocalDate joinDate;

    /** 离组日期 */
    private LocalDate leaveDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
