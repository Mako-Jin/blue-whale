package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.TokenPayload;
import com.bluewhale.auth.application.model.command.SocialAuthorizeCommand;
import com.bluewhale.auth.application.model.command.SocialBindAuthorizeCommand;
import com.bluewhale.auth.application.model.command.SocialCallbackCommand;
import com.bluewhale.auth.application.model.command.SocialLoginByCodeCommand;
import com.bluewhale.auth.application.model.command.SocialUnbindCommand;
import com.bluewhale.auth.application.model.result.LoginResult;
import com.bluewhale.auth.application.model.result.SocialAuthorizeResult;
import com.bluewhale.auth.application.model.result.SocialBindingResult;
import com.bluewhale.auth.application.model.result.SocialCallbackResult;
import com.bluewhale.auth.application.model.social.SocialLoginCodeRecord;
import com.bluewhale.auth.application.model.social.SocialStateRecord;
import com.bluewhale.auth.application.port.CurrentUserProvider;
import com.bluewhale.auth.application.port.SessionLedgerPort;
import com.bluewhale.auth.application.port.SocialAccountProvisioner;
import com.bluewhale.auth.application.port.SocialIdentityProvider;
import com.bluewhale.auth.application.port.SocialLoginCodeStore;
import com.bluewhale.auth.application.port.SocialStateStore;
import com.bluewhale.auth.application.port.SocialWebConfig;
import com.bluewhale.auth.application.port.TenantConfigRepository;
import com.bluewhale.auth.application.port.TokenIssuer;
import com.bluewhale.auth.application.port.UserSessionQuery;
import com.bluewhale.auth.application.service.SocialLoginAppService;
import com.bluewhale.auth.application.service.TenantAppService;
import com.bluewhale.auth.application.service.TenantPasswordPolicyService;
import com.bluewhale.auth.domain.aggregate.SocialBindingAggregate;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.event.AuthDomainEvent;
import com.bluewhale.auth.domain.event.SocialBindingDomainEvent;
import com.bluewhale.auth.domain.model.vo.SocialProfile;
import com.bluewhale.auth.domain.model.vo.SocialProvider;
import com.bluewhale.auth.domain.model.entity.LoginHistoryRecord;
import com.bluewhale.auth.domain.repository.SocialBindingRepository;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.common.core.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 三方登录应用服务实现（权限中心扩展 M2）。
 *
 * <p>只依赖应用层端口与领域仓储，渠道协议细节（钉钉/飞书/企业微信）由
 * {@link SocialIdentityProvider} 适配器隔离；state 与一次性登录码经 Redis 端口存取。
 * 回调裁决一次性消费 state（防重复回调），自动建号受租户
 * {@code auth.social.self_register_enabled} 开关约束。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class SocialLoginAppServiceImpl implements SocialLoginAppService {

    /** 租户自助注册开关的配置键（sys_tenant_config） */
    private static final String CONFIG_SELF_REGISTER_ENABLED = "auth.social.self_register_enabled";
    /** 开关默认值：未配置视为关闭（安全默认） */
    private static final boolean DEFAULT_SELF_REGISTER_ENABLED = false;
    /** 前端回跳错误参数名 */
    private static final String PARAM_ERROR = "error";
    /** 前端回跳登录码参数名 */
    private static final String PARAM_CODE = "code";
    /** 前端回跳渠道参数名 */
    private static final String PARAM_PROVIDER = "provider";

    private final SocialBindingRepository socialBindingRepository;
    private final UserRepository userRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final Map<SocialProvider, SocialIdentityProvider> identityProviders;
    private final SocialStateStore socialStateStore;
    private final SocialLoginCodeStore socialLoginCodeStore;
    private final SocialAccountProvisioner socialAccountProvisioner;
    private final SocialWebConfig socialWebConfig;
    private final TenantAppService tenantAppService;
    private final TenantPasswordPolicyService tenantPasswordPolicyService;
    private final UserSessionQuery userSessionQuery;
    private final TokenIssuer tokenIssuer;
    private final CurrentUserProvider currentUserProvider;
    private final DomainEventPublisher domainEventPublisher;
    private final SessionLedgerPort sessionLedgerPort;

    /**
     * 构造函数：按渠道装配适配器映射（重复支持同一渠道视为装配错误）
     *
     * @param socialBindingRepository 绑定仓储
     * @param userRepository 用户仓储
     * @param tenantConfigRepository 租户配置仓储
     * @param identityProviders 全部渠道适配器
     * @param socialStateStore state 存储
     * @param socialLoginCodeStore 一次性登录码存储
     * @param socialAccountProvisioner 三方账号开通器
     * @param socialWebConfig Web 配置端口
     * @param tenantAppService 租户应用服务
     * @param tenantPasswordPolicyService 租户口令策略服务
     * @param userSessionQuery 用户会话查询
     * @param tokenIssuer 令牌签发端口
     * @param currentUserProvider 当前登录用户提供者
     * @param domainEventPublisher 领域事件发布器
     * @param sessionLedgerPort 会话账本端口（M3 遗留补齐：三方登录记账）
     */
    public SocialLoginAppServiceImpl(SocialBindingRepository socialBindingRepository,
                                     UserRepository userRepository,
                                     TenantConfigRepository tenantConfigRepository,
                                     List<SocialIdentityProvider> identityProviders,
                                     SocialStateStore socialStateStore,
                                     SocialLoginCodeStore socialLoginCodeStore,
                                     SocialAccountProvisioner socialAccountProvisioner,
                                     SocialWebConfig socialWebConfig,
                                     TenantAppService tenantAppService,
                                     TenantPasswordPolicyService tenantPasswordPolicyService,
                                     UserSessionQuery userSessionQuery,
                                     TokenIssuer tokenIssuer,
                                     CurrentUserProvider currentUserProvider,
                                     DomainEventPublisher domainEventPublisher,
                                     SessionLedgerPort sessionLedgerPort) {
        this.socialBindingRepository = socialBindingRepository;
        this.userRepository = userRepository;
        this.tenantConfigRepository = tenantConfigRepository;
        this.identityProviders = identityProviders.stream().collect(Collectors.toUnmodifiableMap(
                SocialIdentityProvider::support, Function.identity()));
        this.socialStateStore = socialStateStore;
        this.socialLoginCodeStore = socialLoginCodeStore;
        this.socialAccountProvisioner = socialAccountProvisioner;
        this.socialWebConfig = socialWebConfig;
        this.tenantAppService = tenantAppService;
        this.tenantPasswordPolicyService = tenantPasswordPolicyService;
        this.userSessionQuery = userSessionQuery;
        this.tokenIssuer = tokenIssuer;
        this.currentUserProvider = currentUserProvider;
        this.domainEventPublisher = domainEventPublisher;
        this.sessionLedgerPort = sessionLedgerPort;
    }

    /**
     * 登录意图：解析目标租户后生成 state 与渠道授权页 URL
     *
     * @param command 授权起始命令
     * @return 授权页 URL
     */
    @Override
    public SocialAuthorizeResult startLogin(SocialAuthorizeCommand command) {
        SocialProvider provider = SocialProvider.of(command.providerCode());
        if (command.tenantCode() == null || command.tenantCode().isBlank()) {
            throw new BizException(400, "三方登录必须指定目标租户编码");
        }
        Long tenantId = tenantAppService.resolveEnabledTenantId(command.tenantCode());
        return startAuthorization(provider, tenantId, null, command.redirect());
    }

    /**
     * 绑定意图：以当前登录用户为目标生成 state 与渠道授权页 URL
     *
     * @param command 绑定授权起始命令
     * @return 授权页 URL
     */
    @Override
    public SocialAuthorizeResult startBind(SocialBindAuthorizeCommand command) {
        SocialProvider provider = SocialProvider.of(command.providerCode());
        var current = currentUserProvider.current();
        // 同一用户同一渠道至多一条有效绑定：发起前先挡，回调侧仍以唯一约束兜底
        socialBindingRepository.findBoundByUserAndProvider(current.userId(), provider).ifPresent(binding -> {
            throw new BizException(400, "已绑定 " + provider.displayName() + " 账号，请先解绑再重新绑定");
        });
        return startAuthorization(provider, current.tokenTenantId(), current.userId(), command.redirect());
    }

    /**
     * 渠道回调裁决（M2 核心）：state 一次性消费 → 换票 → 绑定/邮箱/自助注册三态
     *
     * <p>所有分支（含失败）都以 302 回跳前端授权时指定的相对路径，异常不外抛，
     * 避免把渠道侧浏览器留在裸错误页。</p>
     *
     * @param command 回调命令
     * @return 回调处理结果（含前端回跳 URL）
     */
    @Override
    public SocialCallbackResult completeCallback(SocialCallbackCommand command) {
        SocialProvider provider = SocialProvider.of(command.providerCode());
        SocialStateRecord stateRecord = loadAndConsumeState(command.state(), provider);
        if (stateRecord == null) {
            return redirectResult(provider, null, SocialCallbackResult.SocialCallbackOutcome.INVALID_STATE);
        }
        if (command.code() == null || command.code().isBlank()) {
            log.info("三方回调未携带授权码（用户可能拒绝授权）: provider={}", provider.code());
            return redirectResult(provider, stateRecord.redirect(),
                    SocialCallbackResult.SocialCallbackOutcome.PROVIDER_ERROR);
        }
        SocialProfile profile;
        try {
            profile = identityProviders.get(provider).exchange(command.code());
        } catch (RuntimeException e) {
            log.warn("三方换票失败: provider={}, error={}", provider.code(), e.getMessage());
            return redirectResult(provider, stateRecord.redirect(),
                    SocialCallbackResult.SocialCallbackOutcome.PROVIDER_ERROR);
        }
        Optional<SocialBindingAggregate> existing =
                socialBindingRepository.findByProviderUser(provider, profile.providerUserId().value());
        if (existing.isPresent()) {
            return resolveBound(existing.get(), profile, stateRecord);
        }
        if (stateRecord.bindIntent()) {
            return completeBind(provider, profile, stateRecord);
        }
        return resolveUnboundLogin(provider, profile, stateRecord);
    }

    /**
     * 一次性登录码换令牌
     *
     * @param command 换令牌命令
     * @return 登录结果
     */
    @Override
    public LoginResult exchangeLoginCode(SocialLoginByCodeCommand command) {
        if (command.loginCode() == null || command.loginCode().isBlank()) {
            throw new BizException(401, "登录码无效或已过期");
        }
        SocialLoginCodeRecord record = socialLoginCodeStore.consume(command.loginCode())
                .orElseThrow(() -> new BizException(401, "登录码无效或已过期"));
        var user = userSessionQuery.loadUserById(record.userId(), record.tenantId())
                .orElseThrow(() -> new BizException(401, "账号不存在或已停用"));
        if (!user.enabled()) {
            throw new BizException(401, "账号已停用");
        }
        log.info("三方登录换令牌成功: userId={}, tenantId={}", record.userId(), record.tenantId());
        return buildLoginResult(user);
    }

    /**
     * 当前用户的有效绑定列表
     *
     * @return 绑定列表
     */
    @Override
    public List<SocialBindingResult> listBindings() {
        var current = currentUserProvider.current();
        return socialBindingRepository.listBoundByUser(current.userId()).stream()
                .map(binding -> new SocialBindingResult(binding.getProvider().code(),
                        binding.getProvider().displayName(), binding.getNickname(), binding.getAvatar(),
                        binding.getCreateTime()))
                .toList();
    }

    /**
     * 解绑当前用户的指定渠道绑定
     *
     * @param command 解绑命令
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(SocialUnbindCommand command) {
        SocialProvider provider = SocialProvider.of(command.providerCode());
        var current = currentUserProvider.current();
        SocialBindingAggregate binding = socialBindingRepository
                .findBoundByUserAndProvider(current.userId(), provider)
                .orElseThrow(() -> new BizException(404, "未绑定 " + provider.displayName() + " 账号"));
        binding.unbind();
        socialBindingRepository.save(binding);
        publishBindingEvent(binding, AuthDomainEvent.ChangeType.STATUS_CHANGED);
        log.info("三方解绑完成: userId={}, tenantId={}, provider={}",
                current.userId(), current.tokenTenantId(), provider.code());
    }

    /**
     * 生成 state、落 Redis 并返回渠道授权页 URL
     *
     * @param provider 渠道
     * @param tenantId 目标租户 ID
     * @param bindUserId 绑定意图用户 ID（登录意图为 {@code null}）
     * @param redirect 前端回跳相对路径
     * @return 授权起始结果
     */
    private SocialAuthorizeResult startAuthorization(SocialProvider provider, Long tenantId,
                                                     Long bindUserId, String redirect) {
        String safeRedirect = requireSafeRedirect(redirect);
        String state = UUID.randomUUID().toString().replace("-", "");
        SocialStateRecord record = new SocialStateRecord(provider.code(), tenantId, bindUserId,
                UUID.randomUUID().toString(), safeRedirect, System.currentTimeMillis());
        socialStateStore.save(state, record, socialWebConfig.stateTtlSeconds());
        String authorizeUrl = identityProviders.get(provider).buildAuthorizeUrl(state);
        log.info("三方授权发起: provider={}, tenantId={}, bindIntent={}",
                provider.code(), tenantId, bindUserId != null);
        return new SocialAuthorizeResult(authorizeUrl, state);
    }

    /**
     * 加载并一次性消费 state（防伪造回调与重复回调）
     *
     * @param state state 串
     * @param provider 回调渠道
     * @return state 载荷；非法返回 {@code null}
     */
    private SocialStateRecord loadAndConsumeState(String state, SocialProvider provider) {
        if (state == null || state.isBlank()) {
            return null;
        }
        Optional<SocialStateRecord> loaded = socialStateStore.load(state);
        if (loaded.isEmpty()) {
            log.info("三方回调 state 不存在或已过期: provider={}", provider.code());
            return null;
        }
        if (!loaded.get().provider().equals(provider.code())) {
            log.warn("三方回调渠道与 state 不匹配: provider={}, stateProvider={}",
                    provider.code(), loaded.get().provider());
            return null;
        }
        if (!socialStateStore.consume(state)) {
            log.warn("三方回调 state 重复消费（疑似重放）: provider={}", provider.code());
            return null;
        }
        return loaded.get();
    }

    /**
     * 已有绑定记录的裁决：生效→直登；已解绑→拒绝
     *
     * @param binding 既有绑定
     * @param profile 三方资料
     * @param stateRecord state 载荷
     * @return 回调结果
     */
    private SocialCallbackResult resolveBound(SocialBindingAggregate binding, SocialProfile profile,
                                              SocialStateRecord stateRecord) {
        if (!binding.isBound()) {
            log.info("三方绑定已解绑，拒绝登录: provider={}, providerUserId={}",
                    binding.getProvider().code(), binding.getProviderUserId().value());
            return redirectResult(binding.getProvider(), stateRecord.redirect(),
                    SocialCallbackResult.SocialCallbackOutcome.PROVIDER_ERROR);
        }
        // 绑定意图回调撞上已有绑定（其他账号先绑）：提示前端走解绑流程
        if (stateRecord.bindIntent() && !binding.belongsTo(stateRecord.bindUserId())) {
            log.warn("三方账号已被其他用户绑定，绑定失败: provider={}, bindUserId={}, boundUserId={}",
                    binding.getProvider().code(), stateRecord.bindUserId(), binding.getUserId());
            return redirectResult(binding.getProvider(), stateRecord.redirect(),
                    SocialCallbackResult.SocialCallbackOutcome.PROVIDER_ERROR);
        }
        binding.refreshProfile(profile.unionId(), profile.nickname(), profile.avatar());
        socialBindingRepository.save(binding);
        return issueLoginCode(binding.getProvider(), binding.getTenantId(), binding.getUserId(),
                stateRecord.redirect(), profile.nickname());
    }

    /**
     * 绑定意图完成：为当前用户建立绑定
     *
     * @param provider 渠道
     * @param profile 三方资料
     * @param stateRecord state 载荷
     * @return 回调结果
     */
    private SocialCallbackResult completeBind(SocialProvider provider, SocialProfile profile,
                                              SocialStateRecord stateRecord) {
        SocialBindingAggregate binding = SocialBindingAggregate.create(
                stateRecord.tenantId(), stateRecord.bindUserId(), provider,
                profile.providerUserId().value(), profile.unionId(), profile.nickname(), profile.avatar());
        try {
            socialBindingRepository.save(binding);
        } catch (RuntimeException e) {
            // (provider, providerUserId) 唯一约束兜底：并发下另一账号抢先绑定
            log.warn("三方绑定落库冲突: provider={}, error={}", provider.code(), e.getMessage());
            return redirectResult(provider, stateRecord.redirect(),
                    SocialCallbackResult.SocialCallbackOutcome.PROVIDER_ERROR);
        }
        publishBindingEvent(binding, AuthDomainEvent.ChangeType.CREATED);
        log.info("三方绑定完成: userId={}, tenantId={}, provider={}",
                stateRecord.bindUserId(), stateRecord.tenantId(), provider.code());
        return redirectResult(provider, stateRecord.redirect(),
                SocialCallbackResult.SocialCallbackOutcome.BIND_DONE);
    }

    /**
     * 无绑定记录的登录裁决：同邮箱→要求先登录再绑定；无记录→按租户开关自动建号
     *
     * @param provider 渠道
     * @param profile 三方资料
     * @param stateRecord state 载荷
     * @return 回调结果
     */
    private SocialCallbackResult resolveUnboundLogin(SocialProvider provider, SocialProfile profile,
                                                     SocialStateRecord stateRecord) {
        // 同邮箱未绑定：要求先口令登录后绑定，防止三方侧邮箱接管既有账号（计划 §7-4）
        if (profile.email() != null) {
            Optional<UserAggregate> emailUser =
                    userRepository.findEnabledByEmail(stateRecord.tenantId(), profile.email());
            if (emailUser.isPresent()) {
                log.info("三方邮箱已存在账号，要求先登录再绑定: tenantId={}, provider={}",
                        stateRecord.tenantId(), provider.code());
                return redirectResult(provider, stateRecord.redirect(),
                        SocialCallbackResult.SocialCallbackOutcome.NEED_BIND);
            }
        }
        if (!isSelfRegisterEnabled(stateRecord.tenantId())) {
            log.info("租户未开启三方自助注册，拒绝自动建号: tenantId={}, provider={}",
                    stateRecord.tenantId(), provider.code());
            return redirectResult(provider, stateRecord.redirect(),
                    SocialCallbackResult.SocialCallbackOutcome.REGISTER_DISABLED);
        }
        Long userId = socialAccountProvisioner.provision(stateRecord.tenantId(),
                buildUsername(provider, profile), profile.nickname(), profile.avatar(), profile.email());
        SocialBindingAggregate binding = SocialBindingAggregate.create(
                stateRecord.tenantId(), userId, provider, profile.providerUserId().value(),
                profile.unionId(), profile.nickname(), profile.avatar());
        socialBindingRepository.save(binding);
        publishBindingEvent(binding, AuthDomainEvent.ChangeType.CREATED);
        log.info("三方自动建号并绑定完成: userId={}, tenantId={}, provider={}",
                userId, stateRecord.tenantId(), provider.code());
        return issueLoginCode(provider, stateRecord.tenantId(), userId,
                stateRecord.redirect(), profile.nickname());
    }

    /**
     * 签发一次性登录码并组装直登结果
     *
     * @param provider 渠道
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param redirect 前端回跳路径
     * @param nickname 三方昵称（供前端提示）
     * @return 回调结果
     */
    private SocialCallbackResult issueLoginCode(SocialProvider provider, Long tenantId, Long userId,
                                                String redirect, String nickname) {
        String loginCode = UUID.randomUUID().toString().replace("-", "");
        socialLoginCodeStore.save(loginCode,
                new SocialLoginCodeRecord(userId, tenantId, System.currentTimeMillis()),
                socialWebConfig.loginCodeTtlSeconds());
        Map<String, String> params = new LinkedHashMap<>();
        params.put(PARAM_CODE, loginCode);
        String redirectUrl = buildRedirectUrl(redirect, params);
        return new SocialCallbackResult(SocialCallbackResult.SocialCallbackOutcome.LOGGED_IN,
                redirectUrl, provider.code(), nickname);
    }

    /**
     * 读取租户自助注册开关（未配置视为关闭）
     *
     * @param tenantId 租户 ID
     * @return 开启返回 {@code true}
     */
    private boolean isSelfRegisterEnabled(Long tenantId) {
        Map<String, String> values = tenantConfigRepository.findValues(tenantId,
                List.of(CONFIG_SELF_REGISTER_ENABLED));
        return Optional.ofNullable(values.get(CONFIG_SELF_REGISTER_ENABLED))
                .map(Boolean::parseBoolean)
                .orElse(DEFAULT_SELF_REGISTER_ENABLED);
    }

    /**
     * 自动建号用户名：渠道前缀 + 清洗后的三方标识（长度受控、字符受限）
     *
     * @param provider 渠道
     * @param profile 三方资料
     * @return 初始用户名
     */
    private String buildUsername(SocialProvider provider, SocialProfile profile) {
        String sanitized = profile.providerUserId().value()
                .replaceAll("[^a-zA-Z0-9_]", "_")
                .toLowerCase();
        if (sanitized.length() > 40) {
            sanitized = sanitized.substring(0, 40);
        }
        return provider.code() + "_" + sanitized;
    }

    /**
     * 校验前端回跳路径：必须是站内相对路径（防开放重定向）
     *
     * @param redirect 原始路径
     * @return 校验通过的路径
     */
    private String requireSafeRedirect(String redirect) {
        if (redirect == null || !redirect.startsWith("/") || redirect.startsWith("//")
                || redirect.contains("://")) {
            throw new BizException(400, "回跳路径必须是站内相对路径（以 / 开头）");
        }
        return redirect;
    }

    /**
     * 组装前端回跳 URL（基地址 + 相对路径 + 查询参数）
     *
     * @param redirect 相对路径
     * @param params 查询参数
     * @return 完整 URL
     */
    private String buildRedirectUrl(String redirect, Map<String, String> params) {
        StringBuilder url = new StringBuilder(socialWebConfig.frontendBaseUrl());
        if (!socialWebConfig.frontendBaseUrl().endsWith("/") && redirect.startsWith("/")) {
            url.append(redirect);
        } else {
            url.append(redirect);
        }
        String query = params.entrySet().stream()
                .map(entry -> entry.getKey() + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        return url.toString() + (url.toString().contains("?") ? "&" : "?") + query;
    }

    /**
     * 失败分支统一组装回跳结果
     *
     * @param provider 渠道
     * @param redirect 前端回跳路径（state 丢失时为 {@code null}，回退默认页）
     * @param outcome 结果枚举
     * @return 回调结果
     */
    private SocialCallbackResult redirectResult(SocialProvider provider, String redirect,
                                                SocialCallbackResult.SocialCallbackOutcome outcome) {
        String safeRedirect = redirect != null ? redirect : "/social/callback";
        Map<String, String> params = new HashMap<>();
        params.put(PARAM_ERROR, outcome.name().toLowerCase());
        params.put(PARAM_PROVIDER, provider.code());
        return new SocialCallbackResult(outcome, buildRedirectUrl(safeRedirect, params),
                provider.code(), null);
    }

    /**
     * 发布绑定聚合领域事件
     *
     * @param binding 绑定聚合
     * @param changeType 变更类型
     */
    private void publishBindingEvent(SocialBindingAggregate binding, AuthDomainEvent.ChangeType changeType) {
        domainEventPublisher.publish(new SocialBindingDomainEvent(
                binding.getProvider().code(), binding.getId(), binding.getTenantId(), binding.getUserId(),
                changeType, System.currentTimeMillis()));
    }

    /**
     * 组装登录结果（与口令登录同构：强制改密标记、口令到期、令牌对）
     *
     * @param user 登录用户快照
     * @return 登录结果
     */
    private LoginResult buildLoginResult(com.bluewhale.auth.application.model.AuthenticatedUser user) {
        int maxAgeDays = tenantPasswordPolicyService.maxAgeDays(user.tokenTenantId());
        LocalDateTime now = LocalDateTime.now();
        boolean passwordExpired = UserAggregate.isPasswordExpired(user.passwordUpdatedTime(), now, maxAgeDays);
        long expiresInDays = UserAggregate.passwordExpiresInDays(user.passwordUpdatedTime(), now, maxAgeDays);
        boolean mustChange = user.mustChangePassword() || passwordExpired;
        List<String> roles = List.copyOf(user.roles());
        String refreshToken = tokenIssuer.issueRefreshToken(
                user.tokenTenantId(), user.userId(), user.username(), UUID.randomUUID().toString());
        String accessToken = tokenIssuer.issueAccessToken(
                user.tokenTenantId(), user.userId(), user.username(), roles, mustChange);
        // M3 遗留补齐：三方登录接会话账本（与口令登录同构，降级由端口适配器整体保证）
        recordSocialLedger(user, refreshToken, now);
        return new LoginResult(accessToken, "Bearer",
                tokenIssuer.accessExpireSeconds(),
                user.userId(), user.tokenTenantId(), user.username(), user.nickname(), roles,
                mustChange, passwordExpired, expiresInDays, refreshToken);
    }

    /**
     * 账本：记录一次成功的三方登录令牌签发
     *
     * <p>IP / UA / 设备指纹由 infrastructure 适配器从当前请求上下文解析；
     * 本方法自身不做 try-catch——降级由 {@link SessionLedgerPort} 适配器整体保证。</p>
     *
     * @param user 登录用户快照
     * @param refreshToken 新签发的 refreshToken 原文
     * @param now 签发时刻
     */
    private void recordSocialLedger(com.bluewhale.auth.application.model.AuthenticatedUser user,
                                    String refreshToken, LocalDateTime now) {
        TokenPayload payload = tokenIssuer.parse(refreshToken);
        sessionLedgerPort.recordLogin(new SessionLedgerPort.LoginLedgerEntry(
                user.tokenTenantId(), user.userId(), user.username(), LoginHistoryRecord.TYPE_SOCIAL,
                true, null, null, null, null, null, null, null, null,
                payload == null ? null : payload.jti(), payload == null ? null : payload.familyId(),
                now, now.plusSeconds(tokenIssuer.refreshExpireSeconds())));
    }
}
