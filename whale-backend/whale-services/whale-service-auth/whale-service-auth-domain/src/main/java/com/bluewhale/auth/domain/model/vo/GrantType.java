package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;

/**
 * OAuth2 授权类型值对象（M0）：只允许 OAuth 2.1 保留的三种授权方式。
 *
 * <p><b>不变量</b>：仅接受 {@code authorization_code}、{@code client_credentials}、
 * {@code refresh_token}；{@code password} 与 {@code implicit} 已被 OAuth 2.1 移除，
 * 一律拒绝，避免与现有 {@code /auth/v1/login} 形成两套口令语义。</p>
 *
 * @author BlueWhale
 */
public enum GrantType {

    /** 授权码模式（强制 PKCE） */
    AUTHORIZATION_CODE("authorization_code"),
    /** 客户端凭证模式（服务间调用） */
    CLIENT_CREDENTIALS("client_credentials"),
    /** 刷新令牌模式 */
    REFRESH_TOKEN("refresh_token");

    private final String value;

    /**
     * 构造授权类型
     *
     * @param value 协议字面值
     */
    GrantType(String value) {
        this.value = value;
    }

    /**
     * 获取协议字面值
     *
     * @return 协议中使用的授权类型字符串
     */
    public String value() {
        return value;
    }

    /**
     * 由协议字面值解析授权类型
     *
     * @param value 协议字面值
     * @return 授权类型
     */
    public static GrantType of(String value) {
        for (GrantType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new DomainException(ErrorCode.PARAM_ERROR,
                "不支持的授权类型：" + value + "（仅支持 authorization_code / client_credentials / refresh_token）");
    }
}
