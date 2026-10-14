package com.bluewhale.sts.auth.authentication;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.authz.PermissionChecker;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.PasswordPolicy;
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
 *
 * <p>作废令牌这件事（单个与全部）只有一处实现：{@link SessionRevoker}。
 * 本类不再自己写那两列 —— 它曾经在一处私有方法里，于是"换口令要清会话"这件事
 * 只在两个调用方中的一个落了地（见 {@code docs/decisions/0046}）。
 *
 * <p><b>登出有两条语义，各占一条路径：</b>{@link #logout} 撤**这一个**会话、
 * {@link #logoutAll} 撤**全部**。它们此前共用同一个方法，靠"请求体里有没有刷新令牌"
 * 区分 —— 于是"全部"成了一个没有名字的能力，而它同时又是**省略参数的默认值**：
 * 一个把请求体序列化错的客户端（发 {@code {}}）会静默地把该账号所有设备踢下线。
 * 拆分与代价见 {@code docs/decisions/0047}。
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
    private final PasswordPolicy passwordPolicy;
    private final SessionRevoker sessionRevoker;

    public AuthService(TenantMapper tenantMapper,
                       UserMapper userMapper,
                       RefreshTokenMapper refreshTokenMapper,
                       PermissionChecker permissionChecker,
                       JwtCodec jwtCodec,
                       PasswordHasher passwordHasher,
                       LoginAttemptRecorder loginAttemptRecorder,
                       PasswordChanger passwordChanger,
                       AuthProperties properties,
                       PasswordPolicy passwordPolicy,
                       SessionRevoker sessionRevoker) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.permissionChecker = permissionChecker;
        this.jwtCodec = jwtCodec;
        this.passwordHasher = passwordHasher;
        this.loginAttemptRecorder = loginAttemptRecorder;
        this.passwordChanger = passwordChanger;
        this.properties = properties;
        this.passwordPolicy = passwordPolicy;
        this.sessionRevoker = sessionRevoker;
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
        // 与刷新那条路共用同一个判断（见 PasswordPolicy#isExpired）：两处各写一份的话，
        // 它们会在某一次修改里只改到其中一处，而症状是"登录说没过期、刷新说过期"。
        if (passwordPolicy.isExpired(user.getPasswordExpiresAt(), now)) {
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
     * <p>算法：校验刷新令牌 → 查库确认未被撤销 → 比对摘要 → **校验账号仍可用且口令未过期**
     * → 作废旧令牌并换发新令牌。轮换（rotation）让一个被盗的刷新令牌只有一次可用机会，
     * 且它的下一次使用会立刻触发泄露检测。
     *
     * <p><b>为什么"作废旧令牌"排在全部校验之后：</b>一个失败的请求不该有副作用。
     * 这与 {@link #changePassword} 那句"上面任何一步失败都不该动会话"同源，
     * 而且在这里有一层具体收益：口令已过期的用户反复刷新只会反复得到 {@code 40111}，
     * 手上那个刷新令牌**不会被烧掉**，因此管理员重置口令之后他不必重新登录就能恢复
     * （那时 {@code mustChangePassword} 已经变 1，前端会引导他改密）。
     * 反过来（先撤销再校验）会得到一个没法自愈的会话：令牌被作废、新令牌又没发出来。
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
            sessionRevoker.revokeAll(stored.getUserId(), now);
            throw AuthResultCode.SESSION_INVALID.businessException();
        }
        if (!Digests.sha256Base64(request.getRefreshToken()).equals(stored.getTokenHash())) {
            throw AuthResultCode.TOKEN_INVALID.businessException();
        }

        TenantEntity tenant = tenantMapper.selectById(stored.getTenantId());
        UserEntity user = userMapper.selectById(stored.getUserId());
        if (tenant == null || user == null || !Status.ENABLED.wireName().equals(user.getStatus())) {
            throw AuthResultCode.SESSION_INVALID.businessException();
        }

        // 口令过期**也要在续期这条路上拦**。只在登录拦是不够的：刷新令牌是轮换的
        // （每次续出一个新的、有效期重新起算），只要一直在用就永远不必重新登录 ——
        // 过期于是永不生效，那条策略从"有效期"退化成一句注释。
        // 拦在这里的残留边界是访问令牌的有效期（默认 2 小时）：
        // 最坏情况下到期的人还能用旧会话两小时，之后必须由管理员重置才能回来。
        // 与 logout / changePassword 接受的残留是同一个量级，理由也一样（无状态访问令牌）。
        if (passwordPolicy.isExpired(user.getPasswordExpiresAt(), now)) {
            throw AuthResultCode.PASSWORD_EXPIRED.businessException();
        }

        sessionRevoker.revoke(stored, now);

        return issueTokens(tenant, user, clientIp, userAgent, now);
    }

    /**
     * 登出**这一台设备** —— 撤销请求体里那一个刷新令牌。
     *
     * <p>只撤销**刷新令牌**：访问令牌是无状态的，撤销它需要引入黑名单存储
     * （每个请求都要查一次），而访问令牌本就设得短（默认 2 小时）。
     * 用"短有效期 + 可撤销的长效令牌"换取无状态访问令牌的性能，是标准取舍。
     *
     * <p><b>它只撤这一个。</b>要撤掉全部得走 {@link #logoutAll()} ——
     * 两条语义此前挤在同一个方法里（见类头那段）。顺带一个结论：
     * 本方法**不再需要任何身份**，因为要撤的那一个会话由请求体自己指明了。
     *
     * <p><b>令牌无效时不报错</b>（幂等）：登出的意图是"我这台设备下线"，
     * 而令牌已经失效时这个意图**同样已经达成** —— 回一个错误只会让每个客户端
     * 都去写一段"失败也算成功"的分支，而那段分支里藏着的判断（哪种失败算成功）
     * 正是本方法已经做过的那一次。
     */
    @Transactional
    public void logout(RefreshRequest request) {
        long now = System.currentTimeMillis();
        try {
            JwtPayload payload = jwtCodec.verify(request.getRefreshToken(), now);
            RefreshTokenEntity stored = refreshTokenMapper.selectOne(Wrappers.<RefreshTokenEntity>lambdaQuery()
                    .eq(RefreshTokenEntity::getJti, payload.jti()));
            // 已作废的不再动它 —— 那条判断移进了 SessionRevoker：
            // 撤销时刻只能被写一次，重复登出不该把它推后（见该类的类头）。
            sessionRevoker.revoke(stored, now);
        } catch (JwtVerificationException ex) {
            // 过期与不合法都落在这里（JwtExpiredException 是它的子类）。
            // 令牌本就无效，登出是幂等的：不必报错，也不必再做任何事
            log.debug("登出时令牌不可用，按已登出处理 | message={}", ex.getMessage());
        }
    }

    /**
     * 退出所有设备 —— 撤销**当前用户**的全部刷新令牌（含本次会话手上这一个）。
     *
     * <p><b>为什么它不需要权限码。</b>与 {@code /api/auth/password} 是同一条判据：
     * 能登录就该能踢掉自己的会话。挂上权限码的后果很具体 ——
     * 一个没被授权的用户**永远清不掉自己名下那些会话**，而这恰恰是他在怀疑
     * "账号正被别人登着"时唯一能自救的动作（此刻管理员未必在、也可能正是他要防的人）。
     * 作用域由 {@link AuthContextHolder#require()} 定死：它只能撤自己的，
     * 且**不接受任何传入参数**，因此不存在"拿它去撤别人"的写法。
     * （撤销他人会话是另一件事，不是这条接口的扩展，见 {@code docs/decisions/0047}。）
     *
     * <p><b>为什么未认证时不能"静默成功"。</b>它原先只是登出方法里的一个分支：
     * 拿不到当前用户就什么都不做，却照样返回成功 —— 一句"说做了、其实没做"的回复。
     * 现在由 {@code require()} 明确拒绝（{@code 40104}）：
     * "我不知道你是谁"没法被理解成"已经帮你退出了"。
     *
     * <p><b>访问令牌仍有最长一个令牌有效期（默认 2 小时）的残留</b>，
     * 理由与 {@link #logout} 同（撤销无状态令牌要引入黑名单存储）。
     * 因此"退出所有设备"之后，别的设备上**正在打开的页面**不会立刻掉线，
     * 它会在下一次刷新时失败（{@code 40106}）并被送回登录页。
     */
    @Transactional
    public void logoutAll() {
        AuthContext context = AuthContextHolder.require();
        long now = System.currentTimeMillis();
        sessionRevoker.revokeAll(context.getUserId(), now);
        // 记一条 INFO 而不是像 logout 那样什么都不记：本条是低频且不可撤销的动作
        // （用户本人无法"撤回"这次踢出），排查"我为什么被登出了"时它是唯一的线索。
        log.info("退出所有设备 | userId={} | username={}", context.getUserId(), context.getUsername());
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
     * <p><b>这一步实际由 {@code PasswordChanger#change} 执行，不在本方法里</b> ——
     * 因为管理员重置走的是同一个方法，而它此前**没有**这一步（见
     * {@code docs/decisions/0046}）。原先那句"上面任何一步失败都不该动会话"
     * 因此是换了位置而不是消失：撤销仍是那次变更的最后一步，
     * 强度或防重用没过时，库里的会话一个都不动。
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
        // 撤销全部刷新令牌**不在这里写** —— 它是 PasswordChanger#change 末尾的一步，
        // 与"口令真的换掉了"在同一次提交里。这里原本有一句显式撤销，而另一个调用方
        // （管理员重置）没有，于是同一个动作效力差了一半；修法是把这一步移进那个动作本身。
        // 谁要是觉得此处少了一句、想"顺手补上"，先读 docs/decisions/0046 ——
        // 补上去会变成第二次撤销，而它会把撤销时刻推后。
        passwordChanger.change(user, request.getNewPassword(), PasswordChangeSource.SELF, now);

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
        // 与 MeVO 同源同义：登录与刷新都从这里取值，前端因此不必为了"要不要引导改密"
        // 多发一次请求。刷新这条尤其关键 —— 少了它，用户刷新一次浏览器引导就没了。
        vo.setMustChangePassword(user.getMustChangePassword());
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

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
