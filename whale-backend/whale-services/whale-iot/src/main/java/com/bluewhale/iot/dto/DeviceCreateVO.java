package com.bluewhale.iot.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备创建响应（含一次性下发的 secret——仅本次可见，落库为不可逆凭据）。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceCreateVO extends DeviceVO {

    private static final long serialVersionUID = 1L;

    /** 设备密钥（三元组之一；仅创建响应返回一次，重置走 PUT /devices/{id}/secret） */
    private String secret;
}
