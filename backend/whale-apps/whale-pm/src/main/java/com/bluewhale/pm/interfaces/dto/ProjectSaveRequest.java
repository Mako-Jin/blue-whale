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
    @NotBlank(message = "项目键不能为空")
    @Size(max = 16, message = "项目键长度不能超过 16")
    private String key;

    /** 项目名称 */
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 64, message = "项目名称长度不能超过 64")
    private String name;

    /** 项目类型：SCRUM / KANBAN */
    @NotBlank(message = "项目类型不能为空")
    @Pattern(regexp = "SCRUM|KANBAN", message = "项目类型仅支持 SCRUM / KANBAN")
    private String projectType;

    /** 可见性：PRIVATE / TEAM */
    @Pattern(regexp = "PRIVATE|TEAM", message = "可见性仅支持 PRIVATE / TEAM")
    private String visibility;

    /** 项目负责人用户ID */
    private Long leadUserId;

    /** 项目描述 */
    @Size(max = 512, message = "项目描述长度不能超过 512")
    private String description;
}
