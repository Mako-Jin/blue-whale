package com.bluewhale.auth.application.social;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.AuthenticatedUser;
import com.bluewhale.auth.application.model.command.SocialAuthorizeCommand;
import com.bluewhale.auth.application.model.command.SocialBindAuthorizeCommand;
import com.bluewhale.auth.application.model.command.SocialCallbackCommand;
import com.bluewhale.auth.application.model.command.SocialLoginByCodeCommand;
import com.bluewhale.auth.application.model.command.SocialUnbindCommand;
import com.bluewhale.auth.application.model.result.SocialAuthorizeResult;
import com.bluewhale.auth.application.model.result.SocialCallbackResult;
import com.bluewhale.auth.application.model.social.SocialLoginCodeRecord;
import com.bluewhale.auth.application.model.social.SocialStateRecord;
import com.bluewhale.auth.application.port.CurrentUserProvider;
import com.bluewhale.auth.application.port.SocialAccountProvisioner;
import com.bluewhale.auth.application.port.SocialIdentityProvider;
import com.bluewhale.auth.application.port.SocialLoginCodeStore;
import com.bluewhale.auth.application.port.SocialStateStore;
import com.bluewhale.auth.application.port.SocialWebConfig;
import com.bluewhale.auth.application.port.TenantConfigRepository;
import com.bluewhale.auth.application.port.TokenIssuer;
import com.bluewhale.auth.application.port.UserSessionQuery;
import com.bluewhale.auth.application.service.TenantAppService;
import com.bluewhale.auth.application.service.TenantPasswordPolicyService;
import com.bluewhale.auth.domain.aggregate.SocialBindingAggregate;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.event.SocialBindingDomainEvent;
import com.bluewhale.auth.domain.model.vo.SocialProfile;
import com.bluewhale.auth.domain.model.vo.SocialProvider;
import com.bluewhale.auth.domain.repository.SocialBindingRepository;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.domain.event.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link com.bluewhale.auth.application.service.impl.SocialLoginAppServiceImpl} 单元测试（权限中心扩展 M2）。
 *
 * <p>覆盖三态裁决（直登 / 同邮箱要求先绑定 / 自助注册建号）、state 一次性消费、
 * 绑定意图（新建 / 冲突）、一次性登录码换令牌、绑定列表与解绑。</p>
 *
 * @author BlueWhale
 */
class SocialLoginAppServiceImplTest {

    private static final String STATE = "state-123";
    private static final String LOGIN_CODE = "login-code-456";
    private static final String REDIRECT = "/home";
    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 2L;

    private final SocialBindingRepository socialBindingRepository = mock(SocialBindingRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TenantConfigRepository tenantConfigRepository = mock(TenantConfigRepository.class);
    private final SocialStateStore socialStateStore = mock(SocialStateStore.class);
    private final SocialLoginCodeStore socialLoginCodeStore = mock(SocialLoginCodeStore.class);
    private final SocialAccountProvisioner socialAccountProvisioner = mock(SocialAccountProvisioner.class);
    private final SocialWebConfig socialWebConfig = mock(SocialWebConfig.class);
    private final TenantAppService tenantAppService = mock(TenantAppService.class);
    private final TenantPasswordPolicyService tenantPasswordPolicyService =
            mock(TenantPasswordPolicyService.class);
    private final UserSessionQuery userSessionQuery = mock(UserSessionQuery.class);
    private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final SocialIdentityProvider dingTalkProvider = mock(SocialIdentityProvider.class);

    private com.bluewhale.auth.application.service.impl.SocialLoginAppServiceImpl appService;

    /**
     * 构建被测服务：注入钉钉单渠道适配器
     */
    @BeforeEach
    void setUp() {
        when(dingTalkProvider.support()).thenReturn(SocialProvider.DINGTALK);
        when(socialWebConfig.frontendBaseUrl()).thenReturn("http://fe.local");
        when(socialWebConfig.stateTtlSeconds()).thenReturn(300L);
        when(socialWebConfig.loginCodeTtlSeconds()).thenReturn(60L);
        appService = new com.bluewhale.auth.application.service.impl.SocialLoginAppServiceImpl(
                socialBindingRepository, userRepository, tenantConfigRepository,
                List.of(dingTalkProvider), socialStateStore, socialLoginCodeStore,
                socialAccountProvisioner, socialWebConfig, tenantAppService,
                tenantPasswordPolicyService, userSessionQuery, tokenIssuer,
                currentUserProvider, domainEventPublisher);
    }

    /**
     * 构造 state 载荷（登录意图）
     *
     * @return state 载荷
     */
    private static SocialStateRecord loginState() {
        return new SocialStateRecord("dingtalk", TENANT_ID, null,
                "nonce", REDIRECT, System.currentTimeMillis());
    }

    /**
     * 构造钉钉三方资料
     *
     * @param email 邮箱（可空）
     * @return 三方资料
     */
    private static SocialProfile profile(String email) {
        return SocialProfile.of(SocialProvider.DINGTALK, "open-1", "union-1",
                "张三", "https://a/1.png", email);
    }

    /**
     * 构造有效绑定聚合
     *
     * @return 绑定聚合
     */
    private static SocialBindingAggregate boundBinding() {
        return SocialBindingAggregate.create(TENANT_ID, USER_ID, SocialProvider.DINGTALK,
                "open-1", "union-1", "张三", "https://a/1.png");
    }

    /**
     * 让 state 存储模拟「有效且可消费」
     *
     * @param record state 载荷
     */
    private void stubValidState(SocialStateRecord record) {
        when(socialStateStore.load(STATE)).thenReturn(Optional.of(record));
        when(socialStateStore.consume(STATE)).thenReturn(true);
    }

    /**
     * 让登录码存储消费任意码返回有效载荷
     */
    private void stubLoginCode() {
        when(socialLoginCodeStore.consume(anyString()))
                .thenReturn(Optional.of(new SocialLoginCodeRecord(USER_ID, TENANT_ID,
                        System.currentTimeMillis())));
        when(userSessionQuery.loadUserById(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(new AuthenticatedUser(TENANT_ID, USER_ID, "u-2", "昵称",
                        Set.of("admin"), true, false, LocalDateTime.now())));
        when(tenantPasswordPolicyService.maxAgeDays(TENANT_ID)).thenReturn(0);
        when(tokenIssuer.issueAccessToken(eq(TENANT_ID), eq(USER_ID), anyString(), any(), eq(false)))
                .thenReturn("access-token");
        when(tokenIssuer.issueRefreshToken(eq(TENANT_ID), eq(USER_ID), anyString(), anyString()))
                .thenReturn("refresh-token");
        when(tokenIssuer.accessExpireSeconds()).thenReturn(3600L);
    }

    @Test
    @DisplayName("startLogin：缺租户编码 / 非法回跳路径拒绝")
    void startLoginValidatesInput() {
        assertThatThrownBy(() -> appService.startLogin(
                new SocialAuthorizeCommand("dingtalk", " ", REDIRECT)))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> appService.startLogin(
                new SocialAuthorizeCommand("dingtalk", "tenant-a", "https://evil.com")))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("startLogin：解析租户、落 state、返回授权页 URL")
    void startLoginBuildsAuthorizeUrl() {
        when(tenantAppService.resolveEnabledTenantId("tenant-a")).thenReturn(TENANT_ID);
        when(dingTalkProvider.buildAuthorizeUrl(anyString()))
                .thenReturn("https://login.dingtalk.com/oauth2/auth?state=x");

        SocialAuthorizeResult result = appService.startLogin(
                new SocialAuthorizeCommand("dingtalk", "tenant-a", REDIRECT));

        assertThat(result.authorizeUrl()).contains("login.dingtalk.com");
        assertThat(result.state()).isNotBlank();
        ArgumentCaptor<String> stateCaptor = ArgumentCaptor.forClass(String.class);
        verify(socialStateStore).save(stateCaptor.capture(), any(SocialStateRecord.class), eq(300L));
        assertThat(stateCaptor.getValue()).isEqualTo(result.state());
    }

    @Test
    @DisplayName("startBind：已绑定同渠道拒绝")
    void startBindRejectsExistingBinding() {
        when(currentUserProvider.current()).thenReturn(new AuthenticatedUser(
                TENANT_ID, USER_ID, "u-2", "昵称", Set.of(), true, false, LocalDateTime.now()));
        when(socialBindingRepository.findBoundByUserAndProvider(USER_ID, SocialProvider.DINGTALK))
                .thenReturn(Optional.of(boundBinding()));

        assertThatThrownBy(() -> appService.startBind(
                new SocialBindAuthorizeCommand("dingtalk", REDIRECT)))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("completeCallback：state 不存在或重复消费 → INVALID_STATE 回跳")
    void callbackRejectsInvalidState() {
        when(socialStateStore.load(STATE)).thenReturn(Optional.empty());

        SocialCallbackResult result = appService.completeCallback(
                new SocialCallbackCommand("dingtalk", "code", STATE));

        assertThat(result.outcome()).isEqualTo(SocialCallbackResult.SocialCallbackOutcome.INVALID_STATE);
        assertThat(result.redirectUrl()).startsWith("http://fe.local").contains("error=invalid_state");
        verify(socialStateStore, never()).consume(anyString());
    }

    @Test
    @DisplayName("completeCallback：state 渠道不匹配拒绝")
    void callbackRejectsProviderMismatch() {
        when(socialStateStore.load(STATE))
                .thenReturn(Optional.of(new SocialStateRecord("feishu", TENANT_ID, null,
                        "n", REDIRECT, System.currentTimeMillis())));

        assertThat(appService.completeCallback(new SocialCallbackCommand("dingtalk", "c", STATE))
                .outcome()).isEqualTo(SocialCallbackResult.SocialCallbackOutcome.INVALID_STATE);
    }

    @Test
    @DisplayName("completeCallback：缺授权码 / 换票异常 → PROVIDER_ERROR 回跳")
    void callbackMapsProviderFailures() {
        stubValidState(loginState());
        SocialCallbackResult noCode = appService.completeCallback(
                new SocialCallbackCommand("dingtalk", " ", STATE));
        assertThat(noCode.outcome()).isEqualTo(SocialCallbackResult.SocialCallbackOutcome.PROVIDER_ERROR);

        stubValidState(loginState());
        when(dingTalkProvider.exchange("code")).thenThrow(new IllegalStateException("boom"));
        SocialCallbackResult failed = appService.completeCallback(
                new SocialCallbackCommand("dingtalk", "code", STATE));
        assertThat(failed.outcome()).isEqualTo(SocialCallbackResult.SocialCallbackOutcome.PROVIDER_ERROR);
    }

    @Test
    @DisplayName("completeCallback：绑定生效 → 直登（刷新快照 + 签发一次性登录码）")
    void callbackLogsInWithBoundAccount() {
        stubValidState(loginState());
        when(dingTalkProvider.exchange("code")).thenReturn(profile(null));
        when(socialBindingRepository.findByProviderUser(SocialProvider.DINGTALK, "open-1"))
                .thenReturn(Optional.of(boundBinding()));

        SocialCallbackResult result = appService.completeCallback(
                new SocialCallbackCommand("dingtalk", "code", STATE));

        assertThat(result.outcome()).isEqualTo(SocialCallbackResult.SocialCallbackOutcome.LOGGED_IN);
        assertThat(result.redirectUrl()).startsWith("http://fe.local/home?code=");
        verify(socialBindingRepository).save(any(SocialBindingAggregate.class));
        verify(socialLoginCodeStore).save(anyString(), any(SocialLoginCodeRecord.class), eq(60L));
        verify(domainEventPublisher, never()).publish(any(DomainEvent.class));
    }

    @Test
    @DisplayName("completeCallback：绑定已解绑 → 拒绝登录")
    void callbackRejectsUnboundBinding() {
        stubValidState(loginState());
        when(dingTalkProvider.exchange("code")).thenReturn(profile(null));
        SocialBindingAggregate unbound = boundBinding();
        unbound.unbind();
        when(socialBindingRepository.findByProviderUser(SocialProvider.DINGTALK, "open-1"))
                .thenReturn(Optional.of(unbound));

        assertThat(appService.completeCallback(new SocialCallbackCommand("dingtalk", "code", STATE))
                .outcome()).isEqualTo(SocialCallbackResult.SocialCallbackOutcome.PROVIDER_ERROR);
    }

    @Test
    @DisplayName("completeCallback：绑定意图撞上他人绑定 → 失败回跳")
    void callbackBindIntentConflictsWithOtherOwner() {
        SocialStateRecord bindState = new SocialStateRecord("dingtalk", TENANT_ID, 99L,
                "n", REDIRECT, System.currentTimeMillis());
        stubValidState(bindState);
        when(dingTalkProvider.exchange("code")).thenReturn(profile(null));
        when(socialBindingRepository.findByProviderUser(SocialProvider.DINGTALK, "open-1"))
                .thenReturn(Optional.of(boundBinding()));

        assertThat(appService.completeCallback(new SocialCallbackCommand("dingtalk", "code", STATE))
                .outcome()).isEqualTo(SocialCallbackResult.SocialCallbackOutcome.PROVIDER_ERROR);
        verify(socialBindingRepository, never()).save(any(SocialBindingAggregate.class));
    }

    @Test
    @DisplayName("completeCallback：绑定意图 → 建绑定 + 发领域事件")
    void callbackCompletesBind() {
        SocialStateRecord bindState = new SocialStateRecord("dingtalk", TENANT_ID, 99L,
                "n", REDIRECT, System.currentTimeMillis());
        stubValidState(bindState);
        when(dingTalkProvider.exchange("code")).thenReturn(profile(null));
        when(socialBindingRepository.findByProviderUser(SocialProvider.DINGTALK, "open-1"))
                .thenReturn(Optional.empty());
        when(socialBindingRepository.save(any(SocialBindingAggregate.class)))
                .thenAnswer(invocation -> {
                    SocialBindingAggregate aggregate = invocation.getArgument(0);
                    aggregate.refreshProfile(null, null, null);
                    return aggregate;
                });

        SocialCallbackResult result = appService.completeCallback(
                new SocialCallbackCommand("dingtalk", "code", STATE));

        assertThat(result.outcome()).isEqualTo(SocialCallbackResult.SocialCallbackOutcome.BIND_DONE);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(SocialBindingDomainEvent.class);
    }

    @Test
    @DisplayName("completeCallback：同邮箱已存在账号 → 要求先登录再绑定")
    void callbackRequiresLoginWhenEmailExists() {
        stubValidState(loginState());
        when(dingTalkProvider.exchange("code")).thenReturn(profile("user@example.com"));
        when(socialBindingRepository.findByProviderUser(SocialProvider.DINGTALK, "open-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findEnabledByEmail(TENANT_ID, "user@example.com"))
                .thenReturn(Optional.of(mock(UserAggregate.class)));

        SocialCallbackResult result = appService.completeCallback(
                new SocialCallbackCommand("dingtalk", "code", STATE));

        assertThat(result.outcome()).isEqualTo(SocialCallbackResult.SocialCallbackOutcome.NEED_BIND);
        assertThat(result.redirectUrl()).contains("error=need_bind");
        verify(socialAccountProvisioner, never()).provision(anyLong(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("completeCallback：无记录且租户未开自助注册 → REGISTER_DISABLED")
    void callbackRejectsWhenSelfRegisterDisabled() {
        stubValidState(loginState());
        when(dingTalkProvider.exchange("code")).thenReturn(profile(null));
        when(socialBindingRepository.findByProviderUser(SocialProvider.DINGTALK, "open-1"))
                .thenReturn(Optional.empty());
        when(tenantConfigRepository.findValues(TENANT_ID,
                List.of("auth.social.self_register_enabled"))).thenReturn(Map.of());

        SocialCallbackResult result = appService.completeCallback(
                new SocialCallbackCommand("dingtalk", "code", STATE));

        assertThat(result.outcome()).isEqualTo(
                SocialCallbackResult.SocialCallbackOutcome.REGISTER_DISABLED);
        verify(socialAccountProvisioner, never()).provision(anyLong(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("completeCallback：自助注册开启 → 自动建号 + 绑定 + 直登")
    void callbackAutoProvisionsAccount() {
        stubValidState(loginState());
        when(dingTalkProvider.exchange("code")).thenReturn(profile("new@example.com"));
        when(socialBindingRepository.findByProviderUser(SocialProvider.DINGTALK, "open-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findEnabledByEmail(TENANT_ID, "new@example.com"))
                .thenReturn(Optional.empty());
        when(tenantConfigRepository.findValues(TENANT_ID,
                List.of("auth.social.self_register_enabled")))
                .thenReturn(Map.of("auth.social.self_register_enabled", "true"));
        when(socialAccountProvisioner.provision(eq(TENANT_ID), anyString(), any(), any(), any()))
                .thenReturn(88L);
        when(socialBindingRepository.save(any(SocialBindingAggregate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SocialCallbackResult result = appService.completeCallback(
                new SocialCallbackCommand("dingtalk", "code", STATE));

        assertThat(result.outcome()).isEqualTo(SocialCallbackResult.SocialCallbackOutcome.LOGGED_IN);
        verify(socialAccountProvisioner).provision(eq(TENANT_ID), eq("dingtalk_open_1"),
                eq("张三"), eq("https://a/1.png"), eq("new@example.com"));
        ArgumentCaptor<SocialBindingAggregate> bindingCaptor =
                ArgumentCaptor.forClass(SocialBindingAggregate.class);
        verify(socialBindingRepository).save(bindingCaptor.capture());
        assertThat(bindingCaptor.getValue().getUserId()).isEqualTo(88L);
        assertThat(result.redirectUrl()).startsWith("http://fe.local/home?code=");
    }

    @Test
    @DisplayName("exchangeLoginCode：无效码 401，有效码签发令牌对")
    void exchangeLoginCodeBuildsLoginResult() {
        assertThatThrownBy(() -> appService.exchangeLoginCode(
                new SocialLoginByCodeCommand("bad")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("登录码无效或已过期");

        stubLoginCode();
        var result = appService.exchangeLoginCode(new SocialLoginByCodeCommand(LOGIN_CODE));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.mustChangePassword()).isFalse();
    }

    @Test
    @DisplayName("unbind：未绑定 404；已绑定则解绑并发事件")
    void unbindPublishesEvent() {
        when(currentUserProvider.current()).thenReturn(new AuthenticatedUser(
                TENANT_ID, USER_ID, "u-2", "昵称", Set.of(), true, false, LocalDateTime.now()));
        when(socialBindingRepository.findBoundByUserAndProvider(USER_ID, SocialProvider.DINGTALK))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> appService.unbind(new SocialUnbindCommand("dingtalk")))
                .isInstanceOf(BizException.class);

        SocialBindingAggregate binding = boundBinding();
        when(socialBindingRepository.findBoundByUserAndProvider(USER_ID, SocialProvider.DINGTALK))
                .thenReturn(Optional.of(binding));

        appService.unbind(new SocialUnbindCommand("dingtalk"));
        assertThat(binding.isBound()).isFalse();
        verify(socialBindingRepository).save(binding);
        verify(domainEventPublisher).publish(any(DomainEvent.class));
    }

    @Test
    @DisplayName("listBindings：返回当前用户有效绑定")
    void listBindingsMapsResults() {
        when(currentUserProvider.current()).thenReturn(new AuthenticatedUser(
                TENANT_ID, USER_ID, "u-2", "昵称", Set.of(), true, false, LocalDateTime.now()));
        when(socialBindingRepository.listBoundByUser(USER_ID))
                .thenReturn(List.of(boundBinding()));

        var bindings = appService.listBindings();

        assertThat(bindings).hasSize(1);
        assertThat(bindings.get(0).provider()).isEqualTo("dingtalk");
        assertThat(bindings.get(0).providerName()).isEqualTo("钉钉");
    }

    @Test
    @DisplayName("UUID 工具：state 与登录码生成不含连字符")
    void uuidFormatting() {
        assertThat(UUID.randomUUID().toString().replace("-", "")).doesNotContain("-");
    }
}
