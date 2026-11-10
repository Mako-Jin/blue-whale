package com.bluewhale.iot.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备密钥重置响应（新 secret 仅本次返回一次）。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceSecretResetVO extends DeviceVO {

    private static final long serialVersionUID = 1L;

    /** 新设备密钥（仅重置响应返回一次） */
    private String secret;
}
