package com.bluewhale.mes.workforce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 班组成员保存请求（新增/编辑）
 *
 * @author BlueWhale
 */
@Data
public class MesTeamMemberSaveRequest {

    /** 成员 ID（编辑时必填） */
    private Long id;

    /** 班组 ID */
    @NotNull(message = "{validation.mes.mes-team-member.team-id-required}")
    private Long teamId;

    /** 员工 ID */
    @NotNull(message = "{validation.mes.mes-team-member.employee-id-required}")
    private Long employeeId;

    /** 员工姓名 */
    @NotBlank(message = "{validation.mes.mes-team-member.employee-name-required}")
    @Size(max = 64, message = "{validation.mes.mes-team-member.employee-name-size}")
    private String employeeName;

    /** 是否主操作员：0 否 / 1 是 */
    private Integer primaryOperator;

    /** 技能等级 */
    @Pattern(regexp = "JUNIOR|MIDDLE|SENIOR|EXPERT", message = "{validation.mes.mes-team-member.skill-level-pattern}")
    private String skillLevel;

    /** 入组日期 */
    private LocalDate joinDate;

    /** 离组日期 */
    private LocalDate leaveDate;

    /** 状态：ENABLED/DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.mes.mes-team-member.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-team-member.remark-size}")
    private String remark;
}
