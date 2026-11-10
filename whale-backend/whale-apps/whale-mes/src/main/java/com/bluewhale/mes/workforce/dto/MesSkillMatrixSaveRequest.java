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
    @NotNull(message = "员工不能为空")
    private Long employeeId;

    /** 员工姓名 */
    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 64, message = "员工姓名长度不能超过 64")
    private String employeeName;

    /** 技能编码 */
    @NotBlank(message = "技能编码不能为空")
    @Size(max = 64, message = "技能编码长度不能超过 64")
    private String skillCode;

    /** 技能名称 */
    @NotBlank(message = "技能名称不能为空")
    @Size(max = 128, message = "技能名称长度不能超过 128")
    private String skillName;

    /** 技能等级 */
    @Pattern(regexp = "JUNIOR|MIDDLE|SENIOR|EXPERT", message = "技能等级不合法")
    private String skillLevel;

    /** 认证日期 */
    private LocalDate certifiedAt;

    /** 失效日期 */
    private LocalDate expiryAt;

    /** 状态：ENABLED/EXPIRED/DISABLED */
    @Pattern(regexp = "ENABLED|EXPIRED|DISABLED", message = "状态不合法")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
