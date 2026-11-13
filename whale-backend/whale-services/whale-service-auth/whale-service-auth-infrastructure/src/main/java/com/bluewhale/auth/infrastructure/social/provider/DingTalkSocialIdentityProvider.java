package com.bluewhale.auth.infrastructure.social.provider;

import com.bluewhale.auth.infrastructure.config.SocialLoginProperties;
import com.bluewhale.auth.domain.model.vo.SocialProfile;
import com.bluewhale.auth.domain.model.vo.SocialProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 钉钉扫码登录适配器（权限中心扩展 M2）。
 *
 * <p>新版 OAuth2 协议：{@code /v1.0/oauth2/userAccessToken} 换用户令牌，
 * {@code /v1.0/contact/users/me} 取用户资料（头字段 {@code x-api-ci} 携带用户令牌）。
 * 资料 email 仅在钉钉侧已验证邮箱且应用授权范围包含时返回，可能为空。</p>
 *
 * @author BlueWhale
 */
@Component
public class DingTalkSocialIdentityProvider extends AbstractHttpSocialIdentityProvider {

    /** 授权页地址 */
    private static final String AUTHORIZE_URL = "https://login.dingtalk.com/oauth2/auth";
    /** 用户令牌端点 */
    private static final String TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
    /** 当前用户资料端点 */
    private static final String USER_INFO_URL = "https://api.dingtalk.com/v1.0/contact/users/me";
    /** 用户令牌请求头（钉钉规范：x-api-ci） */
    private static final String USER_TOKEN_HEADER = "x-api-ci";
    /** 服务侧回调路径 */
    private static final String CALLBACK_PATH = "/social/v1/dingtalk/callback";

    /**
     * 构造函数
     *
     * @param properties 三方登录配置
     * @param restClientBuilder Spring 统一 RestClient 构建器
     */
    public DingTalkSocialIdentityProvider(SocialLoginProperties properties,
                                          RestClient.Builder restClientBuilder) {
        super(properties, restClientBuilder);
    }

    /**
     * 本适配器支持的渠道
     *
     * @return 钉钉
     */
    @Override
    public SocialProvider support() {
        return SocialProvider.DINGTALK;
    }

    /**
     * 构建钉钉扫码授权页 URL
     *
     * @param state 防伪随机串（原样透传给渠道，回调时校验）
     * @return 授权页 URL
     */
    @Override
    public String buildAuthorizeUrl(String state) {
        SocialLoginProperties.ProviderCredential credential = requireCredential();
        return AUTHORIZE_URL + "?redirect_uri=" + encode(callbackUri())
                + "&response_type=code&client_id=" + encode(credential.getClientId())
                + "&scope=openid&state=" + encode(state)
                + "&prompt=consent";
    }

    /**
     * 以授权码换取钉钉用户资料
     *
     * @param code 授权码
     * @return 统一三方资料（openId + unionId + 昵称/头像/邮箱）
     */
    @Override
    public SocialProfile exchange(String code) {
        SocialLoginProperties.ProviderCredential credential = requireCredential();
        Map<String, Object> tokenResponse = postJson(TOKEN_URL, Map.of(
                "clientId", credential.getClientId(),
                "clientSecret", credential.getClientSecret(),
                "code", code,
                "grantType", "authorization_code"));
        String accessToken = text(tokenResponse, "accessToken");
        if (accessToken == null || accessToken.isBlank()) {
            throw exchangeFailure("换取用户令牌", "响应缺少 accessToken");
        }
        Map<String, Object> user = getJson(USER_INFO_URL, USER_TOKEN_HEADER, accessToken);
        if (user == null) {
            throw exchangeFailure("获取用户资料", "响应为空");
        }
        return toProfile(text(user, "openId"), text(user, "unionId"),
                text(user, "nick"), text(user, "avatarUrl"), text(user, "email"));
    }

    /**
     * 服务侧回调地址（基地址 + 渠道回调路径）
     *
     * @return 完整回调 URL
     */
    private String callbackUri() {
        return trimTrailingSlash(properties.getCallbackBaseUrl()) + CALLBACK_PATH;
    }

    /**
     * 去除末尾斜杠
     *
     * @param base 原始基地址
     * @return 规整后的基地址
     */
    private String trimTrailingSlash(String base) {
        return base != null && base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
