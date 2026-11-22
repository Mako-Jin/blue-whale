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
    @NotNull(message = "{validation.pm.board.project-id-required}")
    private Long projectId;

    /** 看板名称 */
    @NotBlank(message = "{validation.pm.board.name-required}")
    @Size(max = 64, message = "{validation.pm.board.name-size}")
    private String name;

    /** 看板类型：SCRUM / KANBAN */
    private String boardType;
}
