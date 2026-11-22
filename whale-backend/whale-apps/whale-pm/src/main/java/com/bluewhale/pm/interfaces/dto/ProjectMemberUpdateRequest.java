package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 项目成员角色变更请求
 *
 * @author BlueWhale
 */
@Data
public class ProjectMemberUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目角色：ADMIN / MEMBER / VIEWER */
    @NotBlank(message = "{validation.pm.project-member.project-role-required}")
    @Pattern(regexp = "ADMIN|MEMBER|VIEWER", message = "{validation.pm.project-member.project-role-pattern}")
    private String projectRole;
}
