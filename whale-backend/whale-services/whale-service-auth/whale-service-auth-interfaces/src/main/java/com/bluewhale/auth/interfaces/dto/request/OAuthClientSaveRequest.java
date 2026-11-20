package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * OAuth2 客户端保存请求（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
@Data
public class OAuthClientSaveRequest {

    /** 客户端标识：新建可空（由服务端生成），编辑时忽略 */
    @Pattern(regexp = "^$|^[A-Za-z0-9_-]{4,64}$",
            message = "{validation.auth.oauth.client-id-pattern}")
    private String clientId;

    @NotBlank(message = "{validation.auth.oauth.client-name-required}")
    @Size(max = 128, message = "{validation.auth.oauth.client-name-size}")
    private String clientName;

    /** 回调地址白名单：authorization_code 模式必填，精确匹配 */
    @Size(max = 50, message = "{validation.auth.oauth.redirect-uris-size}")
    private List<String> redirectUris;

    /** 授权类型：仅支持 authorization_code / client_credentials / refresh_token */
    @NotEmpty(message = "{validation.auth.oauth.grant-types-required}")
    private List<String> grantTypes;

    @NotEmpty(message = "{validation.auth.oauth.scopes-required}")
    private List<String> scopes;

    @Min(value = 60, message = "{validation.auth.oauth.access-token-ttl-min}")
    private Integer accessTokenTtl;

    @Min(value = 60, message = "{validation.auth.oauth.refresh-token-ttl-min}")
    private Integer refreshTokenTtl;

    private Boolean autoApprove;

    /** 状态：1 启用 / 0 停用；为空时新建默认启用、编辑保持原状态 */
    @Min(value = 0, message = "{validation.auth.common.status-range}")
    @Max(value = 1, message = "{validation.auth.common.status-range}")
    private Integer status;
}
