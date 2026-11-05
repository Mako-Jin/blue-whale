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
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 200, message = "任务名称长度不能超过 200")
    private String taskName;

    /** 任务类型 */
    @NotBlank(message = "任务类型不能为空")
    @Size(max = 32, message = "任务类型长度不能超过 32")
    private String taskType;

    /** 目标对象类型：DOCUMENT/BOM/PART */
    @Size(max = 16, message = "目标对象类型长度不能超过 16")
    private String targetType;

    /** 目标对象ID */
    private Long targetId;

    /** 目标对象编码 */
    @Size(max = 64, message = "目标对象编码长度不能超过 64")
    private String targetCode;

    /** 单任务执行说明 */
    @Size(max = 500, message = "执行说明长度不能超过 500")
    private String planDesc;

    /** 负责人 */
    private Long ownerId;

    /** 排序号 */
    private Integer sortNo;
}
