package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 网关白名单项响应（内网只读下发，GW-08 支持租户维度）。
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

    /** 作用域：PLATFORM 平台（对全部请求生效）/ TENANT 租户（仅对绑定租户的令牌生效） */
    private String scope;

    /** 租户 ID：0 平台级 / &gt;0 绑定租户 */
    private Long tenantId;
}
