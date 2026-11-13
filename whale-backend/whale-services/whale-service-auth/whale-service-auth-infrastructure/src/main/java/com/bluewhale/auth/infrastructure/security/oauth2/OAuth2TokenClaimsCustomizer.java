package com.bluewhale.auth.infrastructure.security.oauth2;

import com.bluewhale.auth.domain.model.AuthRoleCodes;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.auth.infrastructure.security.LoginUser;
import com.bluewhale.auth.infrastructure.security.PermissionCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * OAuth2 令牌声明定制器（M1）：让授权服务器签发的 JWT 与本服务既有令牌完全同构。
 *
 * <p><b>目标</b>：不引入第二套令牌体系。OAuth2 访问令牌复用同一 HS256 密钥与同一套声明
 * （{@code userId}/{@code tenantId}/{@code username}/{@code roles}/{@code tokenType}/{@code jti}），
 * 因此既有 {@code JwtAuthenticationFilter}、网关黑名单校验与刷新轮换逻辑无需分支即可识别。</p>
 *
 * <p><b>权限裁剪</b>：{@code perm} 声明 = 请求 scope ∩ 用户权限码（超级管理员不裁剪），
 * 资源侧应基于 {@code perm} 做细粒度鉴权；{@code scope} 仍由框架按客户端注册值写入。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class OAuth2TokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    /** 令牌类型声明（与登录令牌保持一致） */
    static final String CLAIM_TOKEN_TYPE = "tokenType";
    /** 访问令牌类型值 */
    static final String TOKEN_TYPE_ACCESS = "access";
    /** 刷新令牌类型值 */
    static final String TOKEN_TYPE_REFRESH = "refresh";
    /** 用户主键声明 */
    static final String CLAIM_USER_ID = "userId";
    /** 租户主键声明 */
    static final String CLAIM_TENANT_ID = "tenantId";
    /** 用户名声明 */
    static final String CLAIM_USERNAME = "username";
    /** 角色 code 声明 */
    static final String CLAIM_ROLES = "roles";
    /** 首登改密标记 */
    static final String CLAIM_PWD_CHANGE_REQUIRED = "pwdChangeRequired";
    /** 裁剪后的权限码声明 */
    static final String CLAIM_PERMISSIONS = "perm";
    /** 客户端标识声明（客户端凭证模式） */
    static final String CLAIM_CLIENT_ID = "clientId";

    private final PermissionCache permissionCache;
    private final RoleRepository roleRepository;

    /**
     * 构造定制器
     *
     * @param permissionCache 权限码缓存（与 {@code PermissionInterceptor} 共用）
     * @param roleRepository 角色仓储（缓存未命中时的回源加载器）
     */
    public OAuth2TokenClaimsCustomizer(PermissionCache permissionCache, RoleRepository roleRepository) {
        this.permissionCache = permissionCache;
        this.roleRepository = roleRepository;
    }

    /**
     * 按令牌类型补充声明
     *
     * @param context JWT 编码上下文
     */
    @Override
    public void customize(JwtEncodingContext context) {
        JwtClaimsSet.Builder claims = context.getClaims();
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            customizeAccessToken(context, claims);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
            claims.claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH);
        }
    }

    /**
     * 访问令牌声明：用户维度补身份与权限，客户端维度补 clientId
     *
     * @param context JWT 编码上下文
     * @param claims 声明构造器
     */
    private void customizeAccessToken(JwtEncodingContext context, JwtClaimsSet.Builder claims) {
        claims.claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS);
        claims.id(UUID.randomUUID().toString());
        Authentication principal = context.getPrincipal();
        if (principal.getPrincipal() instanceof LoginUser loginUser) {
            claims.claim(CLAIM_USER_ID, loginUser.getUserId());
            claims.claim(CLAIM_TENANT_ID, loginUser.getTenantId());
            claims.claim(CLAIM_USERNAME, loginUser.getUsername());
            claims.claim(CLAIM_ROLES, loginUser.getRoles());
            claims.claim(CLAIM_PWD_CHANGE_REQUIRED, loginUser.isMustChangePassword());
            claims.claim(CLAIM_PERMISSIONS, resolvePermissions(loginUser, context.getAuthorizedScopes()));
        } else {
            claims.claim(CLAIM_CLIENT_ID, context.getRegisteredClient().getClientId());
        }
        log.info("[OAUTH2] 签发访问令牌 clientId={} grantType={} principal={} scopes={}",
                context.getRegisteredClient().getClientId(),
                context.getAuthorizationGrantType().getValue(),
                context.getPrincipal().getName(),
                context.getAuthorizedScopes());
    }

    /**
     * 计算本次令牌可行使的权限码
     *
     * @param loginUser 登录用户
     * @param authorizedScopes 已注册的 scope
     * @return 裁剪后的权限码
     */
    private List<String> resolvePermissions(LoginUser loginUser, Set<String> authorizedScopes) {
        if (loginUser.getRoles().contains(AuthRoleCodes.SUPER_ADMIN)) {
            return List.copyOf(authorizedScopes);
        }
        Set<String> codes = permissionCache.getPermissionCodes(loginUser.getUserId(),
                () -> roleRepository.listPermissionCodesByUserId(loginUser.getUserId()));
        return OAuth2ScopePermissionClipper.clip(authorizedScopes, codes);
    }
}
