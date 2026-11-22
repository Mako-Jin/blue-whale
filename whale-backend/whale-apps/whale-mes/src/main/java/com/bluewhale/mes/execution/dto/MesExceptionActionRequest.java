package com.bluewhale.mes.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 异常处理动作请求
 *
 * @author BlueWhale
 */
@Data
public class MesExceptionActionRequest {

    /** 动作：ACKNOWLEDGE/RESOLVE/CLOSE */
    @NotBlank(message = "{validation.mes.mes-exception-action.action-type-required}")
    @Pattern(regexp = "ACKNOWLEDGE|RESOLVE|CLOSE", message = "{validation.mes.mes-exception-action.action-type-pattern}")
    private String actionType;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-exception-action.remark-size}")
    private String remark;

    /** 处理结论（解决 / 关闭时必填） */
    @Size(max = 512, message = "{validation.mes.mes-exception-action.resolution-size}")
    private String resolution;
}
