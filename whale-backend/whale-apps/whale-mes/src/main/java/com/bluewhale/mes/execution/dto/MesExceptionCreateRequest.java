package com.bluewhale.mes.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 现场异常上报请求
 *
 * @author BlueWhale
 */
@Data
public class MesExceptionCreateRequest {

    /** 工单 ID，与任务 ID 至少填一个 */
    private Long workOrderId;

    /** 任务 ID，与工单 ID 至少填一个 */
    private Long taskId;

    /** 异常类型：EQUIPMENT/MATERIAL/QUALITY/OTHER */
    @NotBlank(message = "异常类型不能为空")
    @Pattern(regexp = "EQUIPMENT|MATERIAL|QUALITY|OTHER", message = "异常类型不合法")
    private String exceptionType;

    /** 异常原因编码 */
    @Size(max = 64, message = "异常原因编码长度不能超过 64")
    private String reasonCode;

    /** 异常描述 */
    @NotBlank(message = "异常描述不能为空")
    @Size(max = 512, message = "异常描述长度不能超过 512")
    private String description;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
