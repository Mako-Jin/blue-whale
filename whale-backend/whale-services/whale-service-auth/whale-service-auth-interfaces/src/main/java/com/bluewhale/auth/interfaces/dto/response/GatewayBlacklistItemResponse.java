package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 网关黑名单项响应（内网只读下发）。
 *
 * @author BlueWhale
 */
@Data
public class GatewayBlacklistItemResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 封禁路径（Ant 通配） */
    private String pathPattern;

    /** HTTP 方法：{@code *} 表示不限 */
    private String httpMethod;
}
