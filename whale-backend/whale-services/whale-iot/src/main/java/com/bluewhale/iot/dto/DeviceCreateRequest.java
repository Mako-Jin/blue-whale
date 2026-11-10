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
    @NotNull(message = "产品ID不能为空")
    private Long productId;

    /** 设备名称（产品内唯一；三元组之一） */
    @NotBlank(message = "设备名称不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{1,63}$", message = "设备名称须为字母/数字开头的字母/数字/点/下划线/连字符，2-64 位")
    private String deviceName;

    /** 设备昵称（可空） */
    @Size(max = 128, message = "昵称长度不能超过 128 字")
    private String nickname;
}
