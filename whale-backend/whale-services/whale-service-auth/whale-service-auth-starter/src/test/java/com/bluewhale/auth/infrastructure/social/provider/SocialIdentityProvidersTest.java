package com.bluewhale.auth.infrastructure.social.provider;

import com.bluewhale.auth.domain.model.vo.SocialProfile;
import com.bluewhale.auth.domain.model.vo.SocialProvider;
import com.bluewhale.auth.infrastructure.config.SocialLoginProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * 三渠道适配器单元测试（权限中心扩展 M2）：钉钉 / 飞书 / 企业微信。
 *
 * <p>以 {@link MockRestServiceServer} 绑定 {@link RestClient.Builder}，验证授权 URL 组装、
 * 换票调用序列与响应解析、渠道未启用拒绝。</p>
 *
 * @author BlueWhale
 */
class SocialIdentityProvidersTest {

    private SocialLoginProperties properties;
    private MockRestServiceServer server;
    private RestClient.Builder restClientBuilder;

    /**
     * 构建启用全部渠道的配置与 MockServer
     */
    @BeforeEach
    void setUp() {
        properties = new SocialLoginProperties();
        properties.setEnabled(true);
        properties.setCallbackBaseUrl("http://auth.local/");
        properties.getProviders().put("dingtalk",
                credential("app-key", "app-secret", null));
        properties.getProviders().put("feishu",
                credential("cli-app", "fs-secret", null));
        properties.getProviders().put("wecom",
                credential("corp-id", "corp-secret", "1000001"));
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    /**
     * 构造渠道凭据
     *
     * @param clientId 渠道应用 ID
     * @param clientSecret 渠道密钥
     * @param agentId 企业微信 AgentId（可空）
     * @return 凭据配置
     */
    private static SocialLoginProperties.ProviderCredential credential(
            String clientId, String clientSecret, String agentId) {
        SocialLoginProperties.ProviderCredential credential = new SocialLoginProperties.ProviderCredential();
        credential.setEnabled(true);
        credential.setClientId(clientId);
        credential.setClientSecret(clientSecret);
        credential.setAgentId(agentId);
        return credential;
    }

    @Test
    @DisplayName("钉钉：授权 URL 组装（client_id/redirect_uri/state）")
    void dingTalkBuildsAuthorizeUrl() {
        DingTalkSocialIdentityProvider adapter =
                new DingTalkSocialIdentityProvider(properties, restClientBuilder);

        String url = adapter.buildAuthorizeUrl("state-x");

        assertThat(url).startsWith("https://login.dingtalk.com/oauth2/auth");
        assertThat(url).contains("client_id=app-key")
                .contains("redirect_uri=http%3A%2F%2Fauth.local%2Fsocial%2Fv1%2Fdingtalk%2Fcallback")
                .contains("state=state-x")
                .contains("scope=openid");
    }

    @Test
    @DisplayName("钉钉：换票取 x-api-ci 头调用户资料，解析 openId/unionId")
    void dingTalkExchangesProfile() {
        DingTalkSocialIdentityProvider adapter =
                new DingTalkSocialIdentityProvider(properties, restClientBuilder);
        server.expect(requestTo(Matchers.startsWith(
                        "https://api.dingtalk.com/v1.0/oauth2/userAccessToken")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"accessToken\":\"tok-1\",\"refreshToken\":\"r\",\"expireIn\":7200}",
                        APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith(
                        "https://api.dingtalk.com/v1.0/contact/users/me")))
                .andExpect(request -> assertThat(
                        request.getHeaders().getFirst("x-api-ci")).isEqualTo("tok-1"))
                .andRespond(withSuccess(
                        "{\"openId\":\"open-1\",\"unionId\":\"union-1\",\"nick\":\"张三\","
                                + "\"avatarUrl\":\"https://a/1.png\",\"email\":\"u@ex.com\"}",
                        APPLICATION_JSON));

        SocialProfile profile = adapter.exchange("code-1");

        assertThat(profile.provider()).isEqualTo(SocialProvider.DINGTALK);
        assertThat(profile.providerUserId().value()).isEqualTo("open-1");
        assertThat(profile.unionId()).isEqualTo("union-1");
        assertThat(profile.email()).isEqualTo("u@ex.com");
        server.verify();
    }

    @Test
    @DisplayName("钉钉：换票响应缺 accessToken 抛出")
    void dingTalkRejectsMissingToken() {
        DingTalkSocialIdentityProvider adapter =
                new DingTalkSocialIdentityProvider(properties, restClientBuilder);
        server.expect(requestTo(Matchers.startsWith(
                        "https://api.dingtalk.com/v1.0/oauth2/userAccessToken")))
                .andRespond(withSuccess("{\"errcode\":1}", APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.exchange("code-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("换取用户令牌");
    }

    @Test
    @DisplayName("飞书：授权 URL 组装 + v2 换票解析 data 节点")
    void feishuAuthorizeAndExchange() {
        FeishuSocialIdentityProvider adapter =
                new FeishuSocialIdentityProvider(properties, restClientBuilder);

        String url = adapter.buildAuthorizeUrl("state-y");
        assertThat(url).startsWith("https://open.feishu.cn/open-apis/authen/v1/authorize")
                .contains("app_id=cli-app").contains("state=state-y");

        server.expect(requestTo(Matchers.startsWith(
                        "https://open.feishu.cn/open-apis/authen/v2/oauth/token")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.grant_type").value("authorization_code"))
                .andExpect(jsonPath("$.client_id").value("cli-app"))
                .andExpect(jsonPath("$.redirect_uri").value(
                        "http://auth.local/social/v1/feishu/callback"))
                .andRespond(withSuccess("{\"access_token\":\"tok-2\",\"token_type\":\"bearer\"}",
                        APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith(
                        "https://open.feishu.cn/open-apis/authen/v1/user_info")))
                .andExpect(request -> assertThat(
                        request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer tok-2"))
                .andRespond(withSuccess(
                        "{\"code\":0,\"data\":{\"open_id\":\"ou-1\",\"union_id\":\"un-1\","
                                + "\"name\":\"李四\",\"avatar_url\":\"https://a/2.png\"}}",
                        APPLICATION_JSON));

        SocialProfile profile = adapter.exchange("code-2");
        assertThat(profile.providerUserId().value()).isEqualTo("ou-1");
        assertThat(profile.unionId()).isEqualTo("un-1");
        assertThat(profile.nickname()).isEqualTo("李四");
        assertThat(profile.email()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("企业微信：授权 URL 带 agentid，取企业令牌后凭 code 取 userid")
    void weComAuthorizeAndExchange() {
        WeComSocialIdentityProvider adapter =
                new WeComSocialIdentityProvider(properties, restClientBuilder);

        String url = adapter.buildAuthorizeUrl("state-z");
        assertThat(url).startsWith("https://login.work.weixin.qq.com/wwlogin/sso/login")
                .contains("login_type=CorpApp")
                .contains("appid=corp-id").contains("agentid=1000001")
                .contains("redirect_uri=http%3A%2F%2Fauth.local%2Fsocial%2Fv1%2Fwecom%2Fcallback")
                .contains("state=state-z");

        server.expect(requestTo(Matchers.startsWith(
                        "https://qyapi.weixin.qq.com/cgi-bin/gettoken")))
                .andExpect(request -> assertThat(request.getURI().getQuery())
                        .contains("corpid=corp-id").contains("corpsecret=corp-secret"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"tok-3\",\"errcode\":0}", APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith(
                        "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo")))
                .andExpect(request -> assertThat(request.getURI().getQuery())
                        .contains("access_token=tok-3").contains("code=code-3"))
                .andRespond(withSuccess("{\"errcode\":0,\"userid\":\"corp-user-1\"}",
                        APPLICATION_JSON));

        SocialProfile profile = adapter.exchange("code-3");
        assertThat(profile.providerUserId().value()).isEqualTo("corp-user-1");
        assertThat(profile.unionId()).isNull();
        assertThat(profile.email()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("企业微信：errcode 非 0 抛出换票失败")
    void weComRejectsErrcodeNonZero() {
        WeComSocialIdentityProvider adapter =
                new WeComSocialIdentityProvider(properties, restClientBuilder);
        server.expect(requestTo(Matchers.startsWith(
                        "https://qyapi.weixin.qq.com/cgi-bin/gettoken")))
                .andRespond(withSuccess("{\"errcode\":40013,\"errmsg\":\"invalid corpid\"}",
                        APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.exchange("code-3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("获取企业令牌")
                .hasMessageContaining("40013");
    }

    @Test
    @DisplayName("渠道未启用：授权与换票均拒绝（安全默认）")
    void disabledChannelRejected() {
        properties.setEnabled(true);
        properties.getProviders().get("dingtalk").setEnabled(false);
        DingTalkSocialIdentityProvider adapter =
                new DingTalkSocialIdentityProvider(properties, restClientBuilder);

        assertThatThrownBy(() -> adapter.buildAuthorizeUrl("state"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未启用");

        properties.setEnabled(false);
        assertThatThrownBy(() -> adapter.buildAuthorizeUrl("state"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("总开关");
    }
}
