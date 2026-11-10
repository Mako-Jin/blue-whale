package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 看板保存请求
 *
 * @author BlueWhale
 */
@Data
public class BoardSaveRequest {

    /** 项目ID（仅创建时使用） */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 看板名称 */
    @NotBlank(message = "看板名称不能为空")
    @Size(max = 64, message = "看板名称长度不能超过 64")
    private String name;

    /** 看板类型：SCRUM / KANBAN */
    private String boardType;
}
