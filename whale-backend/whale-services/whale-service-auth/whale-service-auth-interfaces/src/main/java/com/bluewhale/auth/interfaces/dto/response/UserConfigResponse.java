package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户配置响应
 *
 * @author BlueWhale
 */
@Data
public class UserConfigResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置键 */
    private String configKey;

    /** 配置值（JSON） */
    private String configValue;
}
