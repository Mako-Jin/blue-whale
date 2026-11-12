package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * OAuth2 客户端密钥响应（权限中心扩展 M0）：密钥明文仅本次返回。
 *
 * @author BlueWhale
 */
@Data
public class OAuthClientSecretResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String clientId;
    /** 客户端密钥明文：仅本次响应可见 */
    private String clientSecret;
}
