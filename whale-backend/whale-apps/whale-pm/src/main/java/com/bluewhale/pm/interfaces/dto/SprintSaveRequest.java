package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 迭代保存请求
 *
 * @author BlueWhale
 */
@Data
public class SprintSaveRequest {

    /** 项目ID（仅创建时使用） */
    @NotNull(message = "{validation.pm.sprint.project-id-required}")
    private Long projectId;

    /** 迭代名称 */
    @NotBlank(message = "{validation.pm.sprint.name-required}")
    @Size(max = 64, message = "{validation.pm.sprint.name-size}")
    private String name;

    /** 迭代目标 */
    @Size(max = 512, message = "{validation.pm.sprint.goal-size}")
    private String goal;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;
}
