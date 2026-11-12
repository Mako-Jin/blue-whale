package com.bluewhale.auth.application.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * OAuth2 客户端密钥视图对象：仅在创建或轮换时返回一次明文。
 *
 * @author BlueWhale
 */
@Data
@AllArgsConstructor
public class OAuthClientSecretVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String clientId;
    /** 客户端密钥明文：仅本次响应可见，不落库、不写日志 */
    private String clientSecret;
}
