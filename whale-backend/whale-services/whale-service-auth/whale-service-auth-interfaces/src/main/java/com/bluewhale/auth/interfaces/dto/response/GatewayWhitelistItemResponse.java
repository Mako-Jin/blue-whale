package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 网关白名单项响应（内网只读下发）。
 *
 * @author BlueWhale
 */
@Data
public class GatewayWhitelistItemResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 免鉴权路径（Ant 通配） */
    private String pathPattern;

    /** HTTP 方法：{@code *} 表示不限 */
    private String httpMethod;
}
