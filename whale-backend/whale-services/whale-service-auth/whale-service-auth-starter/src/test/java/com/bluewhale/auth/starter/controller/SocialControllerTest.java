package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.SocialAuthorizeCommand;
import com.bluewhale.auth.application.model.command.SocialBindAuthorizeCommand;
import com.bluewhale.auth.application.model.command.SocialCallbackCommand;
import com.bluewhale.auth.application.model.command.SocialLoginByCodeCommand;
import com.bluewhale.auth.application.model.command.SocialUnbindCommand;
import com.bluewhale.auth.application.model.result.LoginResult;
import com.bluewhale.auth.application.model.result.SocialAuthorizeResult;
import com.bluewhale.auth.application.model.result.SocialBindingResult;
import com.bluewhale.auth.application.model.result.SocialCallbackResult;
import com.bluewhale.auth.application.service.SocialLoginAppService;
import com.bluewhale.auth.interfaces.web.AuthCookieManager;
import com.bluewhale.auth.starter.assemble.AuthAssembler;
import com.bluewhale.common.core.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SocialController} 端点单元测试（权限中心扩展 M2）。
 *
 * <p>覆盖授权起始、回调 302、一次性登录码换令牌（Cookie 下发）、绑定/解绑/绑定列表。</p>
 *
 * @author BlueWhale
 */
class SocialControllerTest {

    private static final int PARAM_ERROR_CODE = 400;

    private final SocialLoginAppService socialLoginAppService = mock(SocialLoginAppService.class);
    private final AuthCookieManager cookieManager = mock(AuthCookieManager.class);
    private final SocialController controller = new SocialController(socialLoginAppService,
            cookieManager, Mappers.getMapper(AuthAssembler.class));

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：注册全局异常处理器、校验器与版本策略
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ApiVersionStrategy versionStrategy = new DefaultApiVersionStrategy(
                List.of(new PathApiVersionResolver(1, requestPath -> true)),
                new SemanticApiVersionParser(), false, null, true, null, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.bluewhale.common.core.handler.GlobalExceptionHandler())
                .setValidator(validator)
                .setApiVersionStrategy(versionStrategy)
                .build();
    }

    /**
     * 构造登录结果
     *
     * @return 登录结果
     */
    private static LoginResult loginResult() {
        return new LoginResult("access-token", "Bearer", 3600L, 2L, 1L, "u-2", "昵称",
                List.of("admin"), false, false, -1L, "refresh-token");
    }

    @Test
    @DisplayName("GET /social/v1/dingtalk/authorize：返回渠道授权页 URL")
    void shouldReturnAuthorizeUrl() throws Exception {
        when(socialLoginAppService.startLogin(
                new SocialAuthorizeCommand("dingtalk", "tenant-a", "/home")))
                .thenReturn(new SocialAuthorizeResult("https://login.dingtalk.com/oauth2/auth?x=1",
                        "state-1"));

        mockMvc.perform(get("/social/v1/dingtalk/authorize")
                        .param("tenantCode", "tenant-a")
                        .param("redirect", "/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.authorizeUrl")
                        .value("https://login.dingtalk.com/oauth2/auth?x=1"))
                .andExpect(jsonPath("$.data.state").value("state-1"));
    }

    @Test
    @DisplayName("GET /social/v1/dingtalk/callback：302 重定向回前端回调页")
    void shouldRedirectCallbackToFrontend() throws Exception {
        when(socialLoginAppService.completeCallback(
                new SocialCallbackCommand("dingtalk", "code-1", "state-1")))
                .thenReturn(new SocialCallbackResult(
                        SocialCallbackResult.SocialCallbackOutcome.LOGGED_IN,
                        "http://fe.local/home?code=login-code", "dingtalk", "张三"));

        mockMvc.perform(get("/social/v1/dingtalk/callback")
                        .param("code", "code-1")
                        .param("state", "state-1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://fe.local/home?code=login-code"));
    }

    @Test
    @DisplayName("POST /social/v1/login：一次性登录码换令牌并下发 Cookie")
    void shouldExchangeLoginCode() throws Exception {
        when(socialLoginAppService.exchangeLoginCode(any(SocialLoginByCodeCommand.class)))
                .thenReturn(loginResult());

        mockMvc.perform(post("/social/v1/login")
                        .contentType("application/json")
                        .content("{\"loginCode\":\"code-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.userId").value(2));

        verify(cookieManager).writeAuthCookies(any(), eq("refresh-token"));
    }

    @Test
    @DisplayName("POST /social/v1/login：登录码缺失走参数校验（code=400），无效码返回业务错误码（code=401）")
    void shouldRejectInvalidLoginCode() throws Exception {
        when(socialLoginAppService.exchangeLoginCode(any(SocialLoginByCodeCommand.class)))
                .thenThrow(new com.bluewhale.common.core.exception.BizException(
                        401, "登录码无效或已过期"));

        mockMvc.perform(post("/social/v1/login")
                        .contentType("application/json")
                        .content("{\"loginCode\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE));

        mockMvc.perform(post("/social/v1/login")
                        .contentType("application/json")
                        .content("{\"loginCode\":\"expired-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("登录码无效或已过期"));
    }

    @Test
    @DisplayName("POST /social/v1/bind/authorize：不支持渠道返回业务错误码（400）")
    void shouldRejectUnknownProviderOnBind() throws Exception {
        mockMvc.perform(post("/social/v1/bind/authorize")
                        .contentType("application/json")
                        .content("{\"provider\":\"google\",\"redirect\":\"/x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE));
    }

    @Test
    @DisplayName("POST /social/v1/bind/authorize：返回绑定授权页 URL")
    void shouldReturnBindAuthorizeUrl() throws Exception {
        when(socialLoginAppService.startBind(
                new SocialBindAuthorizeCommand("feishu", "/account")))
                .thenReturn(new SocialAuthorizeResult("https://open.feishu.cn/authorize?x=1", "st-2"));

        mockMvc.perform(post("/social/v1/bind/authorize")
                        .contentType("application/json")
                        .content("{\"provider\":\"feishu\",\"redirect\":\"/account\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizeUrl")
                        .value("https://open.feishu.cn/authorize?x=1"));
    }

    @Test
    @DisplayName("DELETE /social/v1/bind/{provider}：解绑成功返回 ok")
    void shouldUnbind() throws Exception {
        mockMvc.perform(delete("/social/v1/bind/dingtalk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(socialLoginAppService).unbind(new SocialUnbindCommand("dingtalk"));
    }

    @Test
    @DisplayName("GET /social/v1/bindings：返回当前用户绑定列表")
    void shouldListBindings() throws Exception {
        when(socialLoginAppService.listBindings()).thenReturn(List.of(
                new SocialBindingResult("dingtalk", "钉钉", "张三",
                        "https://a/1.png", LocalDateTime.of(2026, 11, 13, 9, 0))));

        mockMvc.perform(get("/social/v1/bindings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].provider").value("dingtalk"))
                .andExpect(jsonPath("$.data[0].providerName").value("钉钉"));
    }
}
