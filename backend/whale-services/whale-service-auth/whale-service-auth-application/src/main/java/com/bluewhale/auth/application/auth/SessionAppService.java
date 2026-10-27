package com.bluewhale.auth.application.auth;

import com.bluewhale.auth.application.auth.port.CredentialAuthenticator;
import com.bluewhale.auth.application.auth.port.CurrentUserProvider;
import com.bluewhale.auth.application.auth.port.RefreshTokenRotationStore;
import com.bluewhale.auth.application.auth.port.TokenBlacklist;
import com.bluewhale.auth.application.auth.port.TokenIssuer;
import com.bluewhale.auth.application.auth.port.UserAccessRevocationPort;
import com.bluewhale.auth.application.auth.port.UserSessionQuery;
import com.bluewhale.auth.application.menu.MenuAppService;
import com.bluewhale.auth.application.permission.PermissionAppService;
import com.bluewhale.auth.application.role.RoleAppService;
import com.bluewhale.auth.application.tenant.TenantAppService;
import com.bluewhale.auth.application.tenant.TenantMembershipAppService;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.auth.vo.AuthMeResponse;
import com.bluewhale.auth.vo.AuthTokenIssue;
import com.bluewhale.auth.vo.LoginResponse;
import com.bluewhale.auth.vo.MenuVO;
import com.bluewhale.auth.vo.MyTenantVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 会话应用服务：登录、刷新 Token、切换租户、登出与当前用户信息。
 *
 * <p>本类只依赖应用层端口与领域仓储，不感知 Spring Security / JWT / MyBatis 等具体实现；
 * 所有出站能力由 infrastructure 适配器注入。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionAppService {

    /** refreshToken 无效 / 过期 / 命中黑名单时的统一对外文案，避免泄露具体失败原因 */
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "refreshToken 无效或已过期";
    /** 已登出令牌被再次使用时的对外文案 */
    private static final String REVOKED_REFRESH_TOKEN_MESSAGE = "refreshToken 已失效，请重新登录";
    /** 复用检测命中（同一 refreshToken 被二次使用）时的对外文案 */
    private static final String REUSE_REFRESH_TOKEN_MESSAGE = "refreshToken 复用检测命中，请重新登录";
    /** 复用检测的安全降级告警场景标识（AUTH-39 §2.6：以告警通道替代 @AuditLog） */
    private static final String SCENE_REFRESH_REUSE = "auth:refresh:reuse";
    /** Bearer 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final CredentialAuthenticator credentialAuthenticator;
    private final TokenIssuer tokenIssuer;
    private final TokenBlacklist tokenBlacklist;
    private final RefreshTokenRotationStore refreshRotationStore;
    private final RefreshRotationProperties refreshRotationProperties;
    private final UserAccessRevocationPort userAccessRevocationPort;
    private final CurrentUserProvider currentUserProvider;
    private final UserSessionQuery userSessionQuery;
    private final UserRepository userRepository;
    private final RoleAppService roleAppService;
    private final MenuAppService menuAppService;
    private final PermissionAppService permissionAppService;
    private final TenantAppService tenantAppService;
    private final TenantMembershipAppService tenantMembershipAppService;

    /**
     * 登录：按（租户, 用户名, 密码）完成凭据校验并签发一对令牌。
     *
     * @param tenantCode 租户编码（可空，按现有规则解析默认租户）
     * @param username 用户名
     * @param rawPassword 明文密码
     * @return 令牌签发结果（响应体含 accessToken；refreshToken 仅由接口层写入 HttpOnly Cookie）
     */
    public AuthTokenIssue login(String tenantCode, String username, String rawPassword) {
        Long tenantId = tenantAppService.resolveEnabledTenantId(tenantCode);
        AuthenticatedUser user = credentialAuthenticator.authenticate(tenantId, username, rawPassword);
        // 记录最后登录时间（聚合行为）
        userRepository.findByIdAcrossTenants(user.userId()).ifPresent(entity -> {
            entity.recordLogin(LocalDateTime.now());
            userRepository.save(entity);
        });
        // 每次登录生成独立 familyId（AUTH-39）：该次会话后续所有轮换令牌共用，复用检测时整族吊销
        return buildAuthTokenIssue(user, UUID.randomUUID().toString());
    }

    /**
     * 刷新：refreshToken 换发新的 accessToken + refreshToken（V-03：令牌由 Cookie 携带）。
     * <p>先做签名/过期与令牌类型校验，再查分布式黑名单：命中（已在登出时失效）直接 401，
     * 阻断「登出后仍可用 refreshToken 换发新 token」的提权路径（REQ-SEC-004）。</p>
     * <p>AUTH-39 一次性轮换（{@code whale.auth.refresh.rotation.enabled=true} 时生效）：
     * family 吊销校验 → 登出黑名单校验 → 以 {@code SET NX} 原子抢占「已消费」标记；
     * 抢占失败即判定复用，整族吊销并告警后返回 401；抢占成功则旧 refreshToken 写入登出黑名单、
     * 以同 familyId 签发新一对令牌。关闭时（默认）行为与既有实现一致。</p>
     * <p>用户级撤销（REQ-SEC-014）：停用、删除、改密后写入的撤销时刻同样作用于 refreshToken，
     * 撤销前签发的 refreshToken 不能再换发新令牌，避免绕过 accessToken 撤销继续访问。</p>
     *
     * @param refreshToken Cookie 中解析出的 refreshToken 原文
     * @return 令牌签发结果（响应体含新 accessToken；新 refreshToken 仅写 Cookie）
     */
    public AuthTokenIssue refresh(String refreshToken) {
        TokenPayload payload = tokenIssuer.parse(refreshToken);
        if (payload == null || !TokenPayload.TYPE_REFRESH.equals(payload.tokenType())) {
            throw new BizException(401, INVALID_REFRESH_TOKEN_MESSAGE);
        }
        String familyId = resolveRotatingFamilyId(payload);
        Long tenantId = resolveTokenTenantId(payload);
        Long userId = resolveTokenUserId(payload);
        tenantAppService.ensureEnabled(tenantId);
        // 一人多租户：关系撤销后 refresh 立即失效，避免旧令牌继续换取目标租户令牌
        tenantMembershipAppService.requireEnabledMembership(userId, tenantId);
        if (tokenBlacklist.contains(refreshToken)) {
            log.debug("refreshToken 命中登出黑名单，拒绝换发新令牌: username={}", payload.subject());
            throw new BizException(401, REVOKED_REFRESH_TOKEN_MESSAGE);
        }
        AuthenticatedUser user = loadEnabledUser(userId, tenantId);
        requireRefreshNotRevoked(userId, tenantId, payload);
        consumeRotatingToken(refreshToken, payload, familyId);
        return buildAuthTokenIssue(user, familyId);
    }

    /**
     * 当前用户可进入的租户列表（一人多租户切换器数据源）。
     *
     * @return 启用且当前可用的租户列表，当前令牌租户标记 {@code current=true}
     */
    public List<MyTenantVO> listTenants() {
        AuthenticatedUser current = currentUserProvider.current();
        return tenantMembershipAppService.listMyTenants(current.userId(), current.tenantId());
    }

    /**
     * 切换租户：同一 {@code userId} / {@code sub} 只更换令牌中的租户上下文与角色。
     *
     * <p>切换成功后将当前 accessToken 与 Cookie 携带的 refreshToken 写入黑名单（开启轮换时
     * 吊销整个 family），避免新旧租户令牌并存；新令牌使用全新 familyId。</p>
     *
     * @param authorization 请求头原始值，形如 {@code Bearer <token>}（可空）
     * @param targetTenantId 目标租户 ID
     * @param currentRefreshToken Cookie 中解析出的当前 refreshToken（可空，携带时一并失效）
     * @return 新租户下的令牌签发结果（新 refreshToken 仅由接口层写 Cookie）
     */
    public AuthTokenIssue switchTenant(String authorization, Long targetTenantId, String currentRefreshToken) {
        AuthenticatedUser current = currentUserProvider.current();
        tenantMembershipAppService.requireSwitchable(current.userId(), targetTenantId);
        AuthenticatedUser switched = userSessionQuery.loadUserById(current.userId(), targetTenantId)
                .orElseThrow(() -> new BizException(401, "账号不存在或已停用"));
        if (!switched.enabled()) {
            throw new BizException(401, "账号已停用");
        }
        invalidateSwitchTokens(authorization, currentRefreshToken);
        log.info("用户切换租户: userId={}, fromTenantId={}, toTenantId={}",
                current.userId(), current.tenantId(), targetTenantId);
        return buildAuthTokenIssue(switched, UUID.randomUUID().toString());
    }

    /**
     * 登出：将 accessToken 与 refreshToken 一并写入分布式黑名单，使其在剩余有效期内立即失效。
     * <p>TTL 取各自令牌剩余有效期，到期后由 Redis 自动清理；
     * Redis 不可用时降级为客户端丢弃 token，不阻断登出流程。</p>
     *
     * @param authorization 请求头原始值，形如 {@code Bearer <token>}；缺失或格式不符时静默跳过
     * @param refreshToken Cookie 携带的 refreshToken（可空）；为空时仅失效 accessToken
     */
    public void logout(String authorization, String refreshToken) {
        String accessToken = resolveBearerToken(authorization);
        if (accessToken != null) {
            tokenBlacklist.add(accessToken);
        } else {
            log.debug("登出请求未携带有效 Bearer token，跳过 accessToken 黑名单写入");
        }
        if (StringUtils.hasText(refreshToken)) {
            tokenBlacklist.add(refreshToken);
        } else {
            log.debug("登出请求未携带 refreshToken，仅失效 accessToken");
        }
    }

    /**
     * 当前登录用户信息：角色 / 权限码 / 可见菜单树。
     * <p>权限码与菜单为「动态路由 + 按钮级权限渲染」的数据源：内置超管（super_admin）
     * 授予全量启用权限码与全部启用可见菜单，其余用户按「用户 → 角色 → 权限点」实际授权过滤。</p>
     *
     * @return 当前登录用户的角色、权限码与可见菜单树
     */
    public AuthMeResponse me() {
        AuthenticatedUser current = currentUserProvider.current();
        List<String> roles = current.roles();
        boolean superAdmin = roles.contains(AuthRoleCodes.SUPER_ADMIN);
        List<String> permissions = superAdmin
                ? permissionAppService.listAllCodes()
                : roleAppService.listPermissionCodesByUserId(current.userId());
        List<MenuVO> menus = superAdmin
                ? menuAppService.treeByPermissionCodes(null)
                : menuAppService.treeByPermissionCodes(Set.copyOf(permissions));
        AuthMeResponse response = new AuthMeResponse();
        response.setTenantId(current.tenantId());
        response.setUserId(current.userId());
        response.setUsername(current.username());
        response.setNickname(current.nickname());
        response.setRoles(roles);
        response.setPermissions(permissions);
        response.setMenus(menus);
        return response;
    }

    /**
     * 从 Authorization 头提取 token 原文
     *
     * @param authorization 请求头原始值
     * @return token 原文；头缺失、前缀不符或 token 段为空时返回 {@code null}
     */
    private static String resolveBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 解析并校验轮换 family 标识（AUTH-39）。
     * <p>轮换开启时：{@code familyId} 缺失（旧令牌，需重登）或该 family 已被吊销一律 401；
     * 轮换关闭时：原样返回载荷值（可能为 {@code null}），不参与校验，保持既有行为。</p>
     *
     * @param payload refreshToken 载荷
     * @return family 标识；关闭态且令牌未携带时返回 {@code null}
     */
    private String resolveRotatingFamilyId(TokenPayload payload) {
        String familyId = payload.familyId();
        if (!refreshRotationProperties.isEnabled()) {
            return familyId;
        }
        if (!StringUtils.hasText(familyId)) {
            log.warn("refreshToken 缺少 familyId（旧令牌），拒绝换发: username={}", payload.subject());
            throw new BizException(401, INVALID_REFRESH_TOKEN_MESSAGE);
        }
        if (refreshRotationStore.isFamilyRevoked(familyId)) {
            log.warn("refreshToken 所属 family 已吊销，拒绝换发: familyId={}, username={}",
                    familyId, payload.subject());
            throw new BizException(401, REVOKED_REFRESH_TOKEN_MESSAGE);
        }
        return familyId;
    }

    /**
     * 从 refreshToken 载荷解析租户 ID（Wave 5.54：令牌必须绑定租户）
     *
     * @param payload refreshToken 载荷
     * @return 租户 ID
     * @throws BizException claim 缺失时抛出 401
     */
    private static Long resolveTokenTenantId(TokenPayload payload) {
        if (payload.tenantId() == null) {
            throw new BizException(401, INVALID_REFRESH_TOKEN_MESSAGE);
        }
        return payload.tenantId();
    }

    /**
     * 从令牌载荷解析用户 ID（Wave 5.54 Phase 5.1：一人多租户按 userId 定位账号）
     *
     * @param payload 令牌载荷
     * @return 用户 ID
     * @throws BizException claim 缺失时抛出 401
     */
    private static Long resolveTokenUserId(TokenPayload payload) {
        if (payload.userId() == null) {
            throw new BizException(401, INVALID_REFRESH_TOKEN_MESSAGE);
        }
        return payload.userId();
    }

    /**
     * 加载并校验用户可用性（按全局 userId 跨租户加载，角色在令牌租户下解析）
     *
     * @param userId 全局用户 ID
     * @param tenantId 令牌租户 ID
     * @return 用户快照
     * @throws BizException 用户不存在或已停用时抛出 401
     */
    private AuthenticatedUser loadEnabledUser(Long userId, Long tenantId) {
        AuthenticatedUser user = userSessionQuery.loadUserById(userId, tenantId)
                .orElseThrow(() -> new BizException(401, INVALID_REFRESH_TOKEN_MESSAGE));
        if (!user.enabled()) {
            throw new BizException(401, "账号已停用");
        }
        return user;
    }

    /**
     * 校验 refreshToken 是否在用户级撤销（停用/删除/改密）之前签发
     *
     * <p>accessToken 已在网关与 auth 过滤器完成撤销校验，但 refresh 接口不携带 accessToken，
     * 若不在此处校验，撤销前窃取的 refreshToken 可换发出撤销时刻之后签发的新令牌，绕过撤销。</p>
     *
     * @param userId 令牌用户 ID
     * @param tenantId 令牌租户 ID
     * @param payload refreshToken 载荷
     * @throws BizException 令牌签发时间不晚于撤销时刻时抛出 401
     */
    private void requireRefreshNotRevoked(Long userId, Long tenantId, TokenPayload payload) {
        if (!userAccessRevocationPort.isRevoked(userId, new Date(payload.issuedAtMillis()))) {
            return;
        }
        log.warn("refreshToken 在用户级撤销前签发，拒绝换发新令牌: userId={}, tenantId={}",
                userId, tenantId);
        throw new BizException(401, REVOKED_REFRESH_TOKEN_MESSAGE);
    }

    /**
     * 切换租户时失效旧令牌：accessToken 与 refreshToken 均写黑名单；
     * 轮换开启且 refreshToken 携带 familyId 时整族吊销，阻断旧会话继续轮换。
     *
     * @param authorization 请求头原始值（可空）
     * @param refreshToken 当前 refreshToken（可空）
     */
    private void invalidateSwitchTokens(String authorization, String refreshToken) {
        String accessToken = resolveBearerToken(authorization);
        if (accessToken != null) {
            tokenBlacklist.add(accessToken);
        }
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        tokenBlacklist.add(refreshToken);
        if (!refreshRotationProperties.isEnabled()) {
            return;
        }
        TokenPayload payload = tokenIssuer.parse(refreshToken);
        String familyId = payload == null ? null : payload.familyId();
        if (StringUtils.hasText(familyId)) {
            refreshRotationStore.revokeFamily(familyId, tokenIssuer.refreshExpireSeconds());
        }
    }

    /**
     * 轮换消费：以 {@code SET NX} 原子抢占「已消费」标记；抢占失败即判定复用，整族吊销并告警后 401
     * <p>轮换关闭时本方法为空操作（不消费、不轮换、不改变既有行为）。抢占成功后将旧 refreshToken
     * 写入登出黑名单，使其在剩余有效期内不可再用（REQ-SEC-004）。</p>
     *
     * @param refreshToken 待消费的 refreshToken 原文
     * @param payload refreshToken 载荷（提供 jti 与用户名）
     * @param familyId 令牌 family 标识
     * @throws BizException 复用检测命中时抛出 401
     */
    private void consumeRotatingToken(String refreshToken, TokenPayload payload, String familyId) {
        if (!refreshRotationProperties.isEnabled()) {
            return;
        }
        long remainingSeconds = tokenIssuer.remainingValiditySeconds(refreshToken);
        if (!refreshRotationStore.markUsed(payload.jti(), remainingSeconds)) {
            refreshRotationStore.revokeFamily(familyId, tokenIssuer.refreshExpireSeconds());
            log.error("refreshToken 复用检测命中，已吊销整个 family: familyId={}, username={}",
                    familyId, payload.subject());
            SecurityDegradationAlerter.alarm(SCENE_REFRESH_REUSE,
                    "refreshToken 复用检测命中，已吊销该 family 全部 refreshToken", null);
            throw new BizException(401, REUSE_REFRESH_TOKEN_MESSAGE);
        }
        tokenBlacklist.add(refreshToken);
    }

    /**
     * 为用户签发 accessToken 与 refreshToken 并组装令牌签发结果（V-03：refreshToken 仅写 Cookie）
     *
     * @param user 登录用户
     * @param familyId 令牌 family 标识（登录时新生成，轮换时沿用）；为 {@code null} 时 refreshToken 不含该 claim
     * @return 令牌签发结果（响应体载荷不含 refreshToken）
     */
    private AuthTokenIssue buildAuthTokenIssue(AuthenticatedUser user, String familyId) {
        LoginResponse response = new LoginResponse();
        List<String> roles = user.roles();
        String refreshToken = tokenIssuer.issueRefreshToken(
                user.tenantId(), user.userId(), user.username(), familyId);
        response.setAccessToken(tokenIssuer.issueAccessToken(user.tenantId(), user.userId(), user.username(), roles));
        response.setTokenType("Bearer");
        response.setExpiresIn(tokenIssuer.accessExpireSeconds());
        response.setTenantId(user.tenantId());
        response.setUsername(user.username());
        response.setNickname(user.nickname());
        response.setRoles(roles);
        // T-03：出厂默认口令轮换后强制首登改密，登录/刷新/切换响应统一透出该标记
        response.setMustChangePassword(user.mustChangePassword());
        return new AuthTokenIssue(response, refreshToken);
    }
}
