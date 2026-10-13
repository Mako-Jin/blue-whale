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
import com.bluewhale.sts.auth.common.enums.PasswordChangeSource;
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
 * 认证应用服务 —— 登录 / 登出 / 刷新 / 查当前用户 / 自助改密。
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
    private final LoginAttemptRecorder loginAttemptRecorder;
    private final PasswordChanger passwordChanger;
    private final AuthProperties properties;

    public AuthService(TenantMapper tenantMapper,
                       UserMapper userMapper,
                       RefreshTokenMapper refreshTokenMapper,
                       PermissionChecker permissionChecker,
                       JwtCodec jwtCodec,
                       PasswordHasher passwordHasher,
                       LoginAttemptRecorder loginAttemptRecorder,
                       PasswordChanger passwordChanger,
                       AuthProperties properties) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.permissionChecker = permissionChecker;
        this.jwtCodec = jwtCodec;
        this.passwordHasher = passwordHasher;
        this.loginAttemptRecorder = loginAttemptRecorder;
        this.passwordChanger = passwordChanger;
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
            recordFailure(user.getId(), now);
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
        // 登录成功即视为"账号恢复正常"：退避层级归零，下次再连续失败时从 5 分钟重新开始。
        // 不归零的后果是"一次误操作会被永久记住"—— 合法用户改一次口令错几次，
        // 之后每次触发锁定都从很长的时长起算，而他已经证明了自己是本人。
        user.setLockLevel(0);
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
        // 认证上下文里没有这个字段（它属于"账号状态"，不属于"身份"），要回查一次库。
        // 代价是一次主键查询 —— 换来的是"管理员重置后本人能立刻被引导去改密"，
        // 而这正是 mustChangePassword 这一列存在的全部意义（此前它没有任何读取方）。
        UserEntity user = userMapper.selectById(context.getUserId());
        vo.setMustChangePassword(user == null ? 0 : user.getMustChangePassword());
        return vo;
    }

    /**
     * 自助改密 —— 本人修改自己的口令。
     *
     * <p>与管理员重置（{@code UserService#resetPassword}）的差别只有三处，
     * 但每一处都不是可选项：
     * <ul>
     *   <li><b>必须验旧口令</b> —— 本接口不挂权限码（能登录就该能改自己的口令，
     *       否则用户会被一个自己都进不去的口令锁在门外），所以"证明你是本人"
     *       只能靠旧口令。不验的话，一次访问令牌泄露就等于一次账号接管：
     *       攻击者进来改掉口令，主人随即被关在门外，且连刷新令牌都被清空。</li>
     *   <li><b>失败必须计数</b> —— 它是一个**新的口令猜测入口**：拿着令牌的人可以
     *       反复猜旧口令，猜中即可改密。走 {@link LoginAttemptRecorder}，
     *       与登录失败共用同一套退避。</li>
     *   <li><b>成功后撤销全部刷新令牌</b> —— 见下。</li>
     * </ul>
     *
     * <p><b>为什么连当前会话的刷新令牌也撤销：</b>改密最常见的动机就是"我怀疑口令泄露了"。
     * 此时若留着攻击者已经换到手的刷新令牌，他下一次刷新就会拿到一套全新的令牌对，
     * 改密便成了一次无害的仪式。做不到"只撤销别人的"是因为**认不出当前用的是哪一个** ——
     * 本请求带的是访问令牌，里面没有刷新令牌的 jti。代价是改完密在访问令牌到期后
     * 需要重新登录，而那更像一个特征而不是缺陷：重新登录一次，正好验证新口令确实能用。
     *
     * <p><b>访问令牌仍有最长一个令牌有效期（默认 2 小时）的残留。</b>这是无状态访问令牌的
     * 固有代价，与 {@link #logout} 是同一条取舍（撤销它要引入黑名单存储，每个请求多查一次）。
     * 但这里的自愈是确定的：攻击者即使握着访问令牌，也**改不了密**（那需要旧口令），
     * 且 2 小时后刷新失败、必然出局。
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        AuthContext context = AuthContextHolder.require();
        long now = System.currentTimeMillis();

        // 按上下文里的主键重新读，不接受调用方传入：口径与其它写操作一致，
        // 也顺带覆盖"令牌还有效、账号已被删"这种边角（读不到即会话失效）
        UserEntity user = userMapper.selectById(context.getUserId());
        if (user == null) {
            throw AuthResultCode.SESSION_INVALID.businessException();
        }

        // 先验旧口令 —— 这是本接口唯一能证明"操作者是本人"的手段
        if (!passwordHasher.matches(request.getOldPassword(), user.getPasswordHash())) {
            // 与登录失败走同一条路（独立事务，不会被本方法的事务回滚带走）。
            // 代价是本人记错旧口令、连试几次也会被锁定 5 分钟 —— 与登录路径的后果一致；
            // 而"改密接口能绕过锁定去猜口令"是更坏的一种不一致。
            recordFailure(user.getId(), now);
            // 复用 40101（凭据不对）而不是新开一个码：这里是**同一件事**（口令不匹配）。
            // 只把文案换成场景化的说法 —— 前端本来就知道自己在调哪个接口，
            // 多开一个码只会让"两个码表达同一件事"多一处。
            throw AuthResultCode.BAD_CREDENTIALS.businessException("当前口令不正确");
        }

        // 改密这件事本人刚做完，不该再被要求"下次登录必须改密"
        user.setMustChangePassword(0);
        passwordChanger.change(user, request.getNewPassword(), PasswordChangeSource.SELF, now);

        // 放在最后：上面任何一步失败都不该动会话 —— 尤其"新口令不合规"时，
        // 用户还得用**当前这个会话**重新填一次，把他踢出去等于让一次输错变成一次登出
        revokeAllForUser(user.getId(), now);

        log.info("自助改密成功 | userId={} | username={}", user.getId(), user.getUsername());
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

    /**
     * 记录一次失败（独立事务，见 {@link LoginAttemptRecorder}）。
     *
     * <p><b>这里必须吞掉它抛出的异常：</b>它的失败意味着"这次没记上失败次数"，
     * 但那不该把"口令错误"这个正常结论变成 500 —— 用户看到的应该是 40101。
     * 退化的后果是"锁定暂时不生效"，比"登录接口因为计数写不进去而全面报错"轻得多。
     */
    private void recordFailure(Long userId, long now) {
        try {
            loginAttemptRecorder.recordFailure(userId, now);
        } catch (RuntimeException ex) {
            log.warn("记录登录失败次数时出错，本次不计数 | userId={} | message={}", userId, ex.getMessage());
        }
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
