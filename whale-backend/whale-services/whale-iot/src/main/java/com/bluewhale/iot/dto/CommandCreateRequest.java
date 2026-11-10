package com.bluewhale.iot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 指令下发请求。
 *
 * @author BlueWhale
 */
@Data
public class CommandCreateRequest {

    /** 服务标识符（物模型 service，如 reboot） */
    @NotBlank(message = "服务标识符不能为空")
    @Pattern(regexp = "^[a-z][a-zA-Z0-9]{0,63}$", message = "标识符须为小写字母开头的字母/数字，1-64 位")
    private String identifier;

    /** 指令参数（对齐物模型服务入参，可空） */
    private Object params;

    /** 幂等键（可空；缺省由服务端签发，重复值返回既有指令） */
    @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "幂等键须为 8-64 位字母/数字/下划线/中划线")
    private String messageId;
}
