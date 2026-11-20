package com.bluewhale.iot.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备更新请求（设备名与产品归属不可改；改名等于换设备，应删了重注册）。
 *
 * @author BlueWhale
 */
@Data
public class DeviceUpdateRequest {

    /** 设备昵称（可空） */
    @Size(max = 128, message = "{validation.iot.device.nickname-size}")
    private String nickname;

    /** 状态：active 启用 / disabled 禁用 */
    @Pattern(regexp = "^(active|disabled)$", message = "{validation.iot.device.status-pattern}")
    private String status;
}
