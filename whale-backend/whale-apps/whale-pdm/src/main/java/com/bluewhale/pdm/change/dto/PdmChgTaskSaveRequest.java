package com.bluewhale.pdm.change.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 变更任务新建/修改请求
 *
 * @author BlueWhale
 */
@Data
public class PdmChgTaskSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务名称 */
    @NotBlank(message = "{validation.pdm.pdm-chg-task.task-name-required}")
    @Size(max = 200, message = "{validation.pdm.pdm-chg-task.task-name-size}")
    private String taskName;

    /** 任务类型 */
    @NotBlank(message = "{validation.pdm.pdm-chg-task.task-type-required}")
    @Size(max = 32, message = "{validation.pdm.pdm-chg-task.task-type-size}")
    private String taskType;

    /** 目标对象类型：DOCUMENT/BOM/PART */
    @Size(max = 16, message = "{validation.pdm.pdm-chg-task.target-type-size}")
    private String targetType;

    /** 目标对象ID */
    private Long targetId;

    /** 目标对象编码 */
    @Size(max = 64, message = "{validation.pdm.pdm-chg-task.target-code-size}")
    private String targetCode;

    /** 单任务执行说明 */
    @Size(max = 500, message = "{validation.pdm.pdm-chg-task.plan-desc-size}")
    private String planDesc;

    /** 负责人 */
    private Long ownerId;

    /** 排序号 */
    private Integer sortNo;
}
