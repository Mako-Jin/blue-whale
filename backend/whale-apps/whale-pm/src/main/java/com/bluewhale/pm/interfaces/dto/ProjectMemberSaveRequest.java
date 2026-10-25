package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 项目成员新增请求
 *
 * @author BlueWhale
 */
@Data
public class ProjectMemberSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成员用户ID */
    @NotNull(message = "成员用户ID不能为空")
    private Long userId;

    /** 项目角色：ADMIN / MEMBER / VIEWER */
    @NotBlank(message = "项目角色不能为空")
    @Pattern(regexp = "ADMIN|MEMBER|VIEWER", message = "项目角色仅支持 ADMIN / MEMBER / VIEWER")
    private String projectRole;
}
