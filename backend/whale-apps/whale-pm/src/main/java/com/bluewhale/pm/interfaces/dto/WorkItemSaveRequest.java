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
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 类型：EPIC / STORY / TASK / BUG / SUBTASK */
    @NotBlank(message = "工作项类型不能为空")
    @Pattern(regexp = "EPIC|STORY|TASK|BUG|SUBTASK",
            message = "工作项类型仅支持 EPIC / STORY / TASK / BUG / SUBTASK")
    private String type;

    /** 标题 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题长度不能超过 255")
    private String title;

    /** 描述 */
    @Size(max = 4000, message = "描述长度不能超过 4000")
    private String description;

    /** 状态ID（创建时为空则使用项目默认状态） */
    private Long statusId;

    /** 优先级：HIGHEST / HIGH / MEDIUM / LOW / LOWEST */
    @Pattern(regexp = "HIGHEST|HIGH|MEDIUM|LOW|LOWEST",
            message = "优先级仅支持 HIGHEST / HIGH / MEDIUM / LOW / LOWEST")
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
    @DecimalMin(value = "0", message = "故事点不能小于 0")
    @Digits(integer = 4, fraction = 1, message = "故事点最多 1 位小数")
    private BigDecimal storyPoints;

    /** 截止日期 */
    private LocalDate dueDate;

    /** 标签（英文逗号分隔） */
    @Size(max = 255, message = "标签长度不能超过 255")
    private String labels;

    /** 扩展字段 JSON */
    @Size(max = 4000, message = "扩展字段长度不能超过 4000")
    private String extensionJson;

    /** 客户端请求ID（创建幂等键） */
    @Size(max = 64, message = "客户端请求ID长度不能超过 64")
    private String clientRequestId;

    /** 版本号（更新时用于乐观锁校验） */
    private Integer version;
}
