package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工作项新增 / 更新请求
 *
 * @author BlueWhale
 */
@Data
public class WorkItemSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    @NotNull(message = "{validation.pm.work-item.project-id-required}")
    private Long projectId;

    /** 类型：EPIC / STORY / TASK / BUG / SUBTASK */
    @NotBlank(message = "{validation.pm.work-item.type-required}")
    @Pattern(regexp = "EPIC|STORY|TASK|BUG|SUBTASK",
            message = "{validation.pm.work-item.type-pattern}")
    private String type;

    /** 标题 */
    @NotBlank(message = "{validation.pm.work-item.title-required}")
    @Size(max = 255, message = "{validation.pm.work-item.title-size}")
    private String title;

    /** 描述 */
    @Size(max = 4000, message = "{validation.pm.work-item.description-size}")
    private String description;

    /** 状态ID（创建时为空则使用项目默认状态） */
    private Long statusId;

    /** 优先级：HIGHEST / HIGH / MEDIUM / LOW / LOWEST */
    @Pattern(regexp = "HIGHEST|HIGH|MEDIUM|LOW|LOWEST",
            message = "{validation.pm.work-item.priority-pattern}")
    private String priority;

    /** 负责人用户ID */
    private Long assigneeId;

    /** 报告人用户ID（创建时为空则取当前用户） */
    private Long reporterId;

    /** 父工作项ID（SUBTASK 必填） */
    private Long parentId;

    /** 迭代ID（M3 启用） */
    private Long sprintId;

    /** 故事点 */
    @DecimalMin(value = "0", message = "{validation.pm.work-item.story-points-min}")
    @Digits(integer = 4, fraction = 1, message = "{validation.pm.work-item.story-points-digits}")
    private BigDecimal storyPoints;

    /** 截止日期 */
    private LocalDate dueDate;

    /** 标签（英文逗号分隔） */
    @Size(max = 255, message = "{validation.pm.work-item.labels-size}")
    private String labels;

    /** 扩展字段 JSON */
    @Size(max = 4000, message = "{validation.pm.work-item.extension-json-size}")
    private String extensionJson;

    /** 客户端请求ID（创建幂等键） */
    @Size(max = 64, message = "{validation.pm.work-item.client-request-id-size}")
    private String clientRequestId;

    /** 版本号（更新时用于乐观锁校验） */
    private Integer version;
}
