package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 项目新增 / 更新请求
 *
 * @author BlueWhale
 */
@Data
public class ProjectSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目键（租户内唯一，大写字母开头） */
    @NotBlank(message = "{validation.pm.project.key-required}")
    @Size(max = 16, message = "{validation.pm.project.key-size}")
    private String key;

    /** 项目名称 */
    @NotBlank(message = "{validation.pm.project.name-required}")
    @Size(max = 64, message = "{validation.pm.project.name-size}")
    private String name;

    /** 项目类型：SCRUM / KANBAN */
    @NotBlank(message = "{validation.pm.project.project-type-required}")
    @Pattern(regexp = "SCRUM|KANBAN", message = "{validation.pm.project.project-type-pattern}")
    private String projectType;

    /** 可见性：PRIVATE / TEAM */
    @Pattern(regexp = "PRIVATE|TEAM", message = "{validation.pm.project.visibility-pattern}")
    private String visibility;

    /** 项目负责人用户ID */
    private Long leadUserId;

    /** 项目描述 */
    @Size(max = 512, message = "{validation.pm.project.description-size}")
    private String description;
}
