package com.bluewhale.auth.infrastructure.social.provider;

import com.bluewhale.auth.infrastructure.config.SocialLoginProperties;
import com.bluewhale.auth.domain.model.vo.SocialProfile;
import com.bluewhale.auth.domain.model.vo.SocialProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 企业微信扫码登录适配器（权限中心扩展 M2）。
 *
 * <p>企业内应用 Web 登录：{@code wwlogin/sso/login} 授权页 → {@code auth/getuserinfo}
 * 凭 code 取成员身份（{@code userid}，非企业成员为 {@code openid}）。企业微信不返回
 * unionId 与邮箱，自动建号场景昵称/邮箱为空属预期。</p>
 *
 * @author BlueWhale
 */
@Component
public class WeComSocialIdentityProvider extends AbstractHttpSocialIdentityProvider {

    /** 授权页地址（新版企业微信 Web 登录） */
    private static final String AUTHORIZE_URL = "https://login.work.weixin.qq.com/wwlogin/sso/login";
    /** 企业应用令牌端点 */
    private static final String TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    /** 凭 code 获取访问用户身份端点 */
    private static final String USER_INFO_URL = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo";
    /** 服务侧回调路径 */
    private static final String CALLBACK_PATH = "/social/v1/wecom/callback";

    /**
     * 构造函数
     *
     * @param properties 三方登录配置
     * @param restClientBuilder Spring 统一 RestClient 构建器
     */
    public WeComSocialIdentityProvider(SocialLoginProperties properties,
                                       RestClient.Builder restClientBuilder) {
        super(properties, restClientBuilder);
    }

    /**
     * 本适配器支持的渠道
     *
     * @return 企业微信
     */
    @Override
    public SocialProvider support() {
        return SocialProvider.WECOM;
    }

    /**
     * 构建企业微信扫码授权页 URL
     *
     * @param state 防伪随机串（原样透传给渠道，回调时校验）
     * @return 授权页 URL
     */
    @Override
    public String buildAuthorizeUrl(String state) {
        SocialLoginProperties.ProviderCredential credential = requireCredential();
        StringBuilder url = new StringBuilder(AUTHORIZE_URL)
                .append("?login_type=CorpApp")
                .append("&appid=").append(encode(credential.getClientId()))
                .append("&redirect_uri=").append(encode(callbackUri()))
                .append("&state=").append(encode(state));
        if (credential.getAgentId() != null && !credential.getAgentId().isBlank()) {
            url.append("&agentid=").append(encode(credential.getAgentId()));
        }
        return url.toString();
    }

    /**
     * 以授权码换取企业微信成员身份
     *
     * @param code 授权码
     * @return 统一三方资料（userid 或 openid 为三方标识；昵称/邮箱为空）
     */
    @Override
    public SocialProfile exchange(String code) {
        SocialLoginProperties.ProviderCredential credential = requireCredential();
        Map<String, Object> tokenResponse = getJson(TOKEN_URL + "?corpid="
                + encode(credential.getClientId()) + "&corpsecret="
                + encode(credential.getClientSecret()), null);
        requireErrcodeZero("获取企业令牌", tokenResponse);
        String accessToken = text(tokenResponse, "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw exchangeFailure("获取企业令牌", "响应缺少 access_token");
        }
        Map<String, Object> user = getJson(USER_INFO_URL + "?access_token=" + encode(accessToken)
                + "&code=" + encode(code), null);
        requireErrcodeZero("获取访问用户身份", user);
        // 企业成员返回 userid，非企业成员返回 openid；二者必居其一
        String providerUserId = text(user, "userid");
        if (providerUserId == null || providerUserId.isBlank()) {
            providerUserId = text(user, "openid");
        }
        if (providerUserId == null || providerUserId.isBlank()) {
            throw exchangeFailure("获取访问用户身份", "响应缺少 userid/openid");
        }
        return toProfile(providerUserId, null, null, null, null);
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
