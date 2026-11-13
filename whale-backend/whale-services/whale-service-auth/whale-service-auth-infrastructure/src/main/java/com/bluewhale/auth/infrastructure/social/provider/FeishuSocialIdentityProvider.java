package com.bluewhale.auth.infrastructure.social.provider;

import com.bluewhale.auth.infrastructure.config.SocialLoginProperties;
import com.bluewhale.auth.domain.model.vo.SocialProfile;
import com.bluewhale.auth.domain.model.vo.SocialProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 飞书扫码登录适配器（权限中心扩展 M2）。
 *
 * <p>OAuth2 v2 换票：{@code /authen/v2/oauth/token}（{@code grant_type=authorization_code}），
 * 用户资料 {@code /authen/v1/user_info}（Bearer 令牌）。email 需要应用开通
 * 「获取用户邮箱」权限且用户授权，可能为空。</p>
 *
 * @author BlueWhale
 */
@Component
public class FeishuSocialIdentityProvider extends AbstractHttpSocialIdentityProvider {

    /** 授权页地址 */
    private static final String AUTHORIZE_URL = "https://open.feishu.cn/open-apis/authen/v1/authorize";
    /** 令牌端点（v2） */
    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v2/oauth/token";
    /** 用户资料端点 */
    private static final String USER_INFO_URL = "https://open.feishu.cn/open-apis/authen/v1/user_info";
    /** 服务侧回调路径 */
    private static final String CALLBACK_PATH = "/social/v1/feishu/callback";

    /**
     * 构造函数
     *
     * @param properties 三方登录配置
     * @param restClientBuilder Spring 统一 RestClient 构建器
     */
    public FeishuSocialIdentityProvider(SocialLoginProperties properties,
                                        RestClient.Builder restClientBuilder) {
        super(properties, restClientBuilder);
    }

    /**
     * 本适配器支持的渠道
     *
     * @return 飞书
     */
    @Override
    public SocialProvider support() {
        return SocialProvider.FEISHU;
    }

    /**
     * 构建飞书扫码授权页 URL
     *
     * @param state 防伪随机串（原样透传给渠道，回调时校验）
     * @return 授权页 URL
     */
    @Override
    public String buildAuthorizeUrl(String state) {
        SocialLoginProperties.ProviderCredential credential = requireCredential();
        return AUTHORIZE_URL + "?redirect_uri=" + encode(callbackUri())
                + "&response_type=code&app_id=" + encode(credential.getClientId())
                + "&state=" + encode(state);
    }

    /**
     * 以授权码换取飞书用户资料
     *
     * @param code 授权码
     * @return 统一三方资料（openId + unionId + 姓名/头像/邮箱）
     */
    @Override
    public SocialProfile exchange(String code) {
        SocialLoginProperties.ProviderCredential credential = requireCredential();
        Map<String, Object> tokenResponse = postJson(TOKEN_URL, Map.of(
                "grant_type", "authorization_code",
                "client_id", credential.getClientId(),
                "client_secret", credential.getClientSecret(),
                "code", code,
                "redirect_uri", callbackUri()));
        String accessToken = text(tokenResponse, "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw exchangeFailure("换取用户令牌",
                    "code=" + text(tokenResponse, "code") + ", msg=" + text(tokenResponse, "msg"));
        }
        Map<String, Object> response = getJson(USER_INFO_URL, accessToken);
        if (response == null) {
            throw exchangeFailure("获取用户资料", "响应为空");
        }
        Object dataObject = response.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = dataObject instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        if (data == null) {
            throw exchangeFailure("获取用户资料", "响应缺少 data 节点");
        }
        return toProfile(text(data, "open_id"), text(data, "union_id"),
                text(data, "name"), text(data, "avatar_url"), text(data, "email"));
    }

    /**
     * 服务侧回调地址（基地址 + 渠道回调路径）
     *
     * @return 完整回调 URL
     */
    private String callbackUri() {
        String base = properties.getCallbackBaseUrl();
        String trimmed = base != null && base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + CALLBACK_PATH;
    }
}
