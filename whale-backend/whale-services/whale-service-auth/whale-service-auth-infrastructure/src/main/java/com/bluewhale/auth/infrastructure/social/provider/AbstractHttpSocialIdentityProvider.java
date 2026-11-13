package com.bluewhale.auth.infrastructure.social.provider;

import com.bluewhale.auth.application.port.SocialIdentityProvider;
import com.bluewhale.auth.domain.model.vo.SocialProfile;
import com.bluewhale.auth.infrastructure.config.SocialLoginProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP 渠道适配器基类（权限中心扩展 M2）：封装 RestClient 调用与凭据启用校验。
 *
 * <p>渠道协议差异（换票端点、用户信息端点、错误码语义）由子类实现；本基类只负责
 * 「凭据已启用」「HTTP 调用异常归一」两件事。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public abstract class AbstractHttpSocialIdentityProvider implements SocialIdentityProvider {

    /** 渠道配置（含凭据与总开关） */
    protected final SocialLoginProperties properties;
    /** 出站 HTTP 客户端（注入 Spring Boot 统一构建器，便于测试 MockRestServiceServer 绑定） */
    protected final RestClient restClient;

    /**
     * 构造函数
     *
     * @param properties 三方登录配置
     * @param restClientBuilder Spring 统一 RestClient 构建器
     */
    protected AbstractHttpSocialIdentityProvider(SocialLoginProperties properties,
                                                 RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    /**
     * 校验总开关与渠道启用状态
     *
     * @return 渠道凭据配置
     */
    protected SocialLoginProperties.ProviderCredential requireCredential() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("三方登录总开关未开启（whale.auth.social.enabled）");
        }
        SocialLoginProperties.ProviderCredential credential =
                properties.getProviders().get(support().code());
        if (credential == null || !credential.isEnabled()) {
            throw new IllegalStateException("三方渠道未启用: " + support().code());
        }
        return credential;
    }

    /**
     * 发起 JSON POST 请求
     *
     * @param url 请求地址
     * @param body 请求体（Map）
     * @return 响应 JSON（Map）
     */
    protected Map<String, Object> postJson(String url, Map<String, Object> body) {
        return restClient.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    /**
     * 发起 GET 请求（可携带 Bearer 令牌）
     *
     * @param url 请求地址
     * @param bearerToken Bearer 令牌（可空）
     * @return 响应 JSON（Map）
     */
    protected Map<String, Object> getJson(String url, String bearerToken) {
        return getJson(url, "Authorization",
                bearerToken == null || bearerToken.isBlank() ? null : "Bearer " + bearerToken);
    }

    /**
     * 发起 GET 请求（可携带自定义头）
     *
     * @param url 请求地址
     * @param headerName 请求头名（可空）
     * @param headerValue 请求头值（可空）
     * @return 响应 JSON（Map）
     */
    protected Map<String, Object> getJson(String url, String headerName, String headerValue) {
        RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(url);
        if (headerName != null && headerValue != null && !headerValue.isBlank()) {
            spec = spec.header(headerName, headerValue);
        }
        return spec.retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    /**
     * 统一换票异常：渠道错误码非 0 或响应结构缺失时抛出，由应用层归一为 PROVIDER_ERROR
     *
     * @param step 业务步骤描述
     * @param detail 错误详情
     * @return 供 {@code throw} 使用的异常
     */
    protected IllegalStateException exchangeFailure(String step, String detail) {
        log.warn("三方换票失败: provider={}, step={}, detail={}", support().code(), step, detail);
        return new IllegalStateException(support().displayName() + step + "失败: " + detail);
    }

    /**
     * 校验渠道响应错误码（企业微信系 errcode，0 为成功）
     *
     * @param step 业务步骤描述
     * @param response 响应 JSON
     */
    protected void requireErrcodeZero(String step, Map<String, Object> response) {
        if (response == null) {
            throw exchangeFailure(step, "响应为空");
        }
        Object errcode = response.get("errcode");
        if (errcode != null && !Integer.valueOf(0).equals(((Number) errcode).intValue())) {
            throw exchangeFailure(step, "errcode=" + errcode + ", errmsg=" + response.get("errmsg"));
        }
    }

    /**
     * 从响应 Map 取字符串字段
     *
     * @param response 响应 JSON
     * @param key 字段名
     * @return 字符串值；缺失返回 {@code null}
     */
    protected String text(Map<String, Object> response, String key) {
        Object value = response == null ? null : response.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * URL 编码查询参数值
     *
     * @param value 原始值
     * @return 编码后的值
     */
    protected String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 转换为统一三方资料
     *
     * @param providerUserId 三方用户标识
     * @param unionId unionId（可空）
     * @param nickname 昵称（可空）
     * @param avatar 头像（可空）
     * @param email 邮箱（可空）
     * @return 三方资料
     */
    protected SocialProfile toProfile(String providerUserId, String unionId, String nickname,
                                      String avatar, String email) {
        return SocialProfile.of(support(), providerUserId, unionId, nickname, avatar, email);
    }
}
