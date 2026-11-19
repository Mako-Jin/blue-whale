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
    @NotBlank(message = "{validation.iot.command.identifier-required}")
    @Pattern(regexp = "^[a-z][a-zA-Z0-9]{0,63}$", message = "{validation.iot.command.identifier-pattern}")
    private String identifier;

    /** 指令参数（对齐物模型服务入参，可空） */
    private Object params;

    /** 幂等键（可空；缺省由服务端签发，重复值返回既有指令） */
    @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "{validation.iot.command.message-id-pattern}")
    private String messageId;
}
