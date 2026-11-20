package com.bluewhale.iot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备创建请求（secret 由服务端签发，仅在创建响应返回一次）。
 *
 * @author BlueWhale
 */
@Data
public class DeviceCreateRequest {

    /** 所属产品ID */
    @NotNull(message = "{validation.iot.device.product-required}")
    private Long productId;

    /** 设备名称（产品内唯一；三元组之一） */
    @NotBlank(message = "{validation.iot.device.name-required}")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{1,63}$", message = "{validation.iot.device.name-pattern}")
    private String deviceName;

    /** 设备昵称（可空） */
    @Size(max = 128, message = "{validation.iot.device.nickname-size}")
    private String nickname;
}
