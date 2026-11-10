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
    @NotBlank(message = "动作不能为空")
    @Pattern(regexp = "ACKNOWLEDGE|RESOLVE|CLOSE", message = "动作不合法")
    private String actionType;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 处理结论（解决 / 关闭时必填） */
    @Size(max = 512, message = "处理结论长度不能超过 512")
    private String resolution;
}
