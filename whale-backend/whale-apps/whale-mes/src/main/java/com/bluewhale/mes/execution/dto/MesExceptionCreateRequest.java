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
    @NotBlank(message = "{validation.mes.mes-exception.exception-type-required}")
    @Pattern(regexp = "EQUIPMENT|MATERIAL|QUALITY|OTHER", message = "{validation.mes.mes-exception.exception-type-pattern}")
    private String exceptionType;

    /** 异常原因编码 */
    @Size(max = 64, message = "{validation.mes.mes-exception.reason-code-size}")
    private String reasonCode;

    /** 异常描述 */
    @NotBlank(message = "{validation.mes.mes-exception.description-required}")
    @Size(max = 512, message = "{validation.mes.mes-exception.description-size}")
    private String description;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-exception.remark-size}")
    private String remark;
}
