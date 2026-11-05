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
    @NotNull(message = "班组不能为空")
    private Long teamId;

    /** 员工 ID */
    @NotNull(message = "员工不能为空")
    private Long employeeId;

    /** 员工姓名 */
    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 64, message = "员工姓名长度不能超过 64")
    private String employeeName;

    /** 是否主操作员：0 否 / 1 是 */
    private Integer primaryOperator;

    /** 技能等级 */
    @Pattern(regexp = "JUNIOR|MIDDLE|SENIOR|EXPERT", message = "技能等级不合法")
    private String skillLevel;

    /** 入组日期 */
    private LocalDate joinDate;

    /** 离组日期 */
    private LocalDate leaveDate;

    /** 状态：ENABLED/DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态不合法")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
