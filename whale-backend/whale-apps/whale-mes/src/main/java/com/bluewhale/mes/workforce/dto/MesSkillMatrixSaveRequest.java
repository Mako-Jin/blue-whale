package com.bluewhale.mes.workforce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 技能矩阵保存请求（新增/编辑）
 *
 * @author BlueWhale
 */
@Data
public class MesSkillMatrixSaveRequest {

    /** 技能记录 ID（编辑时必填） */
    private Long id;

    /** 员工 ID */
    @NotNull(message = "{validation.mes.mes-skill-matrix.employee-id-required}")
    private Long employeeId;

    /** 员工姓名 */
    @NotBlank(message = "{validation.mes.mes-skill-matrix.employee-name-required}")
    @Size(max = 64, message = "{validation.mes.mes-skill-matrix.employee-name-size}")
    private String employeeName;

    /** 技能编码 */
    @NotBlank(message = "{validation.mes.mes-skill-matrix.skill-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-skill-matrix.skill-code-size}")
    private String skillCode;

    /** 技能名称 */
    @NotBlank(message = "{validation.mes.mes-skill-matrix.skill-name-required}")
    @Size(max = 128, message = "{validation.mes.mes-skill-matrix.skill-name-size}")
    private String skillName;

    /** 技能等级 */
    @Pattern(regexp = "JUNIOR|MIDDLE|SENIOR|EXPERT", message = "{validation.mes.mes-skill-matrix.skill-level-pattern}")
    private String skillLevel;

    /** 认证日期 */
    private LocalDate certifiedAt;

    /** 失效日期 */
    private LocalDate expiryAt;

    /** 状态：ENABLED/EXPIRED/DISABLED */
    @Pattern(regexp = "ENABLED|EXPIRED|DISABLED", message = "{validation.mes.mes-skill-matrix.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-skill-matrix.remark-size}")
    private String remark;
}
