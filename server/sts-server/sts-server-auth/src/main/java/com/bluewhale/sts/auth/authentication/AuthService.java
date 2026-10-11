package com.bluewhale.sts.auth.authentication;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.authz.PermissionChecker;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.codec.Digests;
import com.bluewhale.sts.auth.common.codec.JwtCodec;
import com.bluewhale.sts.auth.common.codec.JwtExpiredException;
import com.bluewhale.sts.auth.common.codec.JwtPayload;
import com.bluewhale.sts.auth.common.codec.JwtVerificationException;
import com.bluewhale.sts.auth.common.codec.PasswordHasher;
import com.bluewhale.sts.auth.common.enums.Status;
import com.bluewhale.sts.auth.context.AuthContext;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.auth.tenant.TenantEntity;
import com.bluewhale.sts.auth.tenant.TenantMapper;
import com.bluewhale.sts.auth.user.UserEntity;
import com.bluewhale.sts.auth.user.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * 认证应用服务 —— 登录 / 登出 / 刷新 / 查当前用户。
 *
 * <p><b>这是参照实现里最空的一块。</b>{@code SystemTerminator} 的
 * {@code AuthApplicationServiceImpl.authentication(...)} 直接 {@code return null}，
 * 密码认证 Provider 整段被注释，刷新与登出连接口都没有。
 * 本服务把这条链路真正实现出来。
 *
 * <p><b>三处刻意的安全取舍：</b>
 * <ol>
 *   <li><b>不区分"账号不存在"与"口令错误"</b> —— 都返回同一个码。
 *       区分开来等于给攻击者一个账号枚举接口，配合手机号/邮箱还能直接撞库。</li>
 *   <li><b>先做口令校验再泄露任何账号状态</b>：锁定、停用等状态虽会返回不同的码，
 *       但**只在口令正确之后**才提示，否则同样构成信息泄露。</li>
 *   <li><b>刷新令牌轮换 + 重放检测</b>：每次刷新都作废旧令牌、换发新令牌；
 *       若一个已作废的 jti 被再次使用，视为令牌泄露，撤销该用户全部刷新令牌。</li>
 * </ol>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PermissionChecker permissionChecker;
    private final JwtCodec jwtCodec;
    private final PasswordHasher passwordHasher;
    private final AuthProperties properties;

    public AuthService(TenantMapper tenantMapper,
                       UserMapper userMapper,
                       RefreshTokenMapper refreshTokenMapper,
                       PermissionChecker permissionChecker,
                       JwtCodec jwtCodec,
                       PasswordHasher passwordHasher,
                       AuthProperties properties) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.permissionChecker = permissionChecker;
        this.jwtCodec = jwtCodec;
        this.passwordHasher = passwordHasher;
        this.properties = properties;
    }

    @Transactional
    public LoginVO login(LoginRequest request, String clientIp, String userAgent) {
        long now = System.currentTimeMillis();

        TenantEntity tenant = tenantMapper.selectOne(Wrappers.<TenantEntity>lambdaQuery()
                .eq(TenantEntity::getTenantCode, request.getTenantCode()));
        if (tenant == null || !Status.ENABLED.wireName().equals(tenant.getStatus())) {
            // 与"口令错误"同一个码：租户是否存在同样属于不该泄露的信息
            throw AuthResultCode.BAD_CREDENTIALS.businessException();
        }

        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getTenantId, tenant.getId())
                .eq(UserEntity::getUsername, request.getUsername()));
        if (user == null) {
            throw AuthResultCode.BAD_CREDENTIALS.businessException();
        }

        // 口令正确之前，一概不透露账号状态
        if (!passwordHasher.matches(request.getPassword(), user.getPasswordHash())) {
            registerFailure(user, now);
            throw AuthResultCode.BAD_CREDENTIALS.businessException();
        }

        // 口令已正确，此时才给出精确的状态提示
        if (user.getLockedUntil() != null && user.getLockedUntil() > now) {
            throw AuthResultCode.ACCOUNT_LOCKED.businessException();
        }
        if (!Status.ENABLED.wireName().equals(user.getStatus())) {
            throw AuthResultCode.ACCOUNT_DISABLED.businessException();
        }
        if (user.getPasswordExpiresAt() != null && user.getPasswordExpiresAt() <= now) {
            throw AuthResultCode.PASSWORD_EXPIRED.businessException();
        }

        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLockReason(null);
        user.setLastLoginAt(now);
        // 存储参数落后于当前配置时，趁登录拿到明文口令的机会顺带升级哈希
        if (passwordHasher.needsRehash(user.getPasswordHash())) {
            user.setPasswordHash(passwordHasher.hash(request.getPassword()));
            user.setPasswordAlgorithm(passwordHasher.algorithmId());
            user.setPasswordUpdatedAt(now);
        }
        userMapper.updateById(user);

        log.info("登录成功 | tenant={} | username={} | ip={}", tenant.getTenantCode(), user.getUsername(), clientIp);
        return issueTokens(tenant, user, clientIp, userAgent, now);
    }

    /**
     * 刷新访问令牌。
     *
     * <p>算法：校验刷新令牌 → 查库确认未被撤销 → 比对摘要 → **作废旧令牌并换发新令牌**。
     * 轮换（rotation）让一个被盗的刷新令牌只有一次可用机会，
     * 且它的下一次使用会立刻触发泄露检测。
     */
    @Transactional
    public LoginVO refresh(RefreshRequest request, String clientIp, String userAgent) {
        long now = System.currentTimeMillis();

        JwtPayload payload;
        try {
            payload = jwtCodec.verify(request.getRefreshToken(), now);
        } catch (JwtExpiredException ex) {
            throw AuthResultCode.SESSION_INVALID.businessException();
        } catch (JwtVerificationException ex) {
            throw AuthResultCode.TOKEN_INVALID.businessException();
        }
        if (!payload.isRefreshToken()) {
            throw AuthResultCode.TOKEN_INVALID.businessException("该令牌不是刷新令牌");
        }

        RefreshTokenEntity stored = refreshTokenMapper.selectOne(Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getJti, payload.jti()));
        if (stored == null) {
            throw AuthResultCode.SESSION_INVALID.businessException();
        }
        if (stored.getRevoked() != null && stored.getRevoked() != 0) {
            // 已作废的令牌被再次使用 —— 极可能是泄露，撤销该用户全部刷新令牌
            log.warn("检测到已作废刷新令牌被重放 | userId={} | jti={}", stored.getUserId(), stored.getJti());
            revokeAllForUser(stored.getUserId(), now);
            throw AuthResultCode.SESSION_INVALID.businessException();
        }
        if (!Digests.sha256Base64(request.getRefreshToken()).equals(stored.getTokenHash())) {
            throw AuthResultCode.TOKEN_INVALID.businessException();
        }

        stored.setRevoked(1);
        stored.setRevokedAt(now);
        refreshTokenMapper.updateById(stored);

        TenantEntity tenant = tenantMapper.selectById(stored.getTenantId());
        UserEntity user = userMapper.selectById(stored.getUserId());
        if (tenant == null || user == null || !Status.ENABLED.wireName().equals(user.getStatus())) {
            throw AuthResultCode.SESSION_INVALID.businessException();
        }
        return issueTokens(tenant, user, clientIp, userAgent, now);
    }

    /**
     * 登出。
     *
     * <p>只撤销**刷新令牌**：访问令牌是无状态的，撤销它需要引入黑名单存储
     * （每个请求都要查一次），而访问令牌本就设得短（默认 2 小时）。
     * 用"短有效期 + 可撤销的长效令牌"换取无状态访问令牌的性能，是标准取舍。
     *
     * <p>不传刷新令牌时撤销该用户的全部刷新令牌（"退出所有设备"）。
     */
    @Transactional
    public void logout(RefreshRequest request) {
        long now = System.currentTimeMillis();
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            try {
                JwtPayload payload = jwtCodec.verify(request.getRefreshToken(), now);
                RefreshTokenEntity stored = refreshTokenMapper.selectOne(Wrappers.<RefreshTokenEntity>lambdaQuery()
                        .eq(RefreshTokenEntity::getJti, payload.jti()));
                if (stored != null && (stored.getRevoked() == null || stored.getRevoked() == 0)) {
                    stored.setRevoked(1);
                    stored.setRevokedAt(now);
                    refreshTokenMapper.updateById(stored);
                }
            } catch (JwtVerificationException ex) {
                // 令牌本就无效，登出是幂等的：不必报错，也不必再做任何事
                log.debug("登出时令牌不可用，按已登出处理 | message={}", ex.getMessage());
            }
            return;
        }
        Long userId = AuthContextHolder.currentUserId();
        if (userId != null) {
            revokeAllForUser(userId, now);
        }
    }

    /** 当前登录用户信息（含按需装载的角色与权限） */
    @Transactional(readOnly = true)
    public MeVO currentUser() {
        AuthContext context = AuthContextHolder.require();
        Set<String> permissions = permissionChecker.permissionsOf(context);

        MeVO vo = new MeVO();
        vo.setUserId(context.getUserId());
        vo.setUsername(context.getUsername());
        vo.setDisplayName(context.getDisplayName());
        vo.setTenantId(context.getTenantId());
        vo.setRoles(context.getRoleCodes());
        vo.setPermissions(permissions);
        return vo;
    }

    private LoginVO issueTokens(TenantEntity tenant, UserEntity user,
                                String clientIp, String userAgent, long now) {
        String accessJti = UUID.randomUUID().toString();
        String accessToken = jwtCodec.issue(user.getId(), tenant.getId(), user.getUsername(),
                "access", accessJti, now, properties.getJwt().getAccessTtl());

        String refreshJti = UUID.randomUUID().toString();
        String refreshToken = jwtCodec.issue(user.getId(), tenant.getId(), user.getUsername(),
                "refresh", refreshJti, now, properties.getJwt().getRefreshTtl());

        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setTenantId(tenant.getId());
        token.setUserId(user.getId());
        token.setJti(refreshJti);
        // 只存摘要：库被读走也拿不到可直接使用的令牌
        token.setTokenHash(Digests.sha256Base64(refreshToken));
        token.setIssuedAt(now);
        token.setExpiresAt(now + properties.getJwt().getRefreshTtl().toMillis());
        token.setRevoked(0);
        token.setClientIp(clientIp);
        token.setUserAgent(truncate(userAgent, 512));
        refreshTokenMapper.insert(token);

        AuthContext loadContext = new AuthContext();
        loadContext.setUserId(user.getId());
        loadContext.setTenantId(tenant.getId());
        Set<String> permissions = permissionChecker.permissionsOf(loadContext);

        LoginVO vo = new LoginVO();
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setExpiresIn(properties.getJwt().getAccessTtl().toSeconds());
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setTenantId(tenant.getId());
        vo.setTenantCode(tenant.getTenantCode());
        vo.setRoles(loadContext.getRoleCodes());
        vo.setPermissions(permissions);
        return vo;
    }

    private void registerFailure(UserEntity user, long now) {
        int attempts = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
        user.setFailedAttempts(attempts);
        if (attempts >= properties.getLogin().getMaxFailedAttempts()) {
            user.setLockedUntil(now + properties.getLogin().getLockDuration().toMillis());
            user.setLockReason("连续登录失败 " + attempts + " 次");
            log.warn("账号因连续登录失败被锁定 | userId={} | attempts={}", user.getId(), attempts);
        }
        userMapper.updateById(user);
    }

    private void revokeAllForUser(Long userId, long now) {
        RefreshTokenEntity update = new RefreshTokenEntity();
        update.setRevoked(1);
        update.setRevokedAt(now);
        refreshTokenMapper.update(update, Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getUserId, userId)
                .eq(RefreshTokenEntity::getRevoked, 0));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
