package com.bluewhale.sts.auth.context;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.Set;

/**
 * 当前请求的认证上下文 —— "这次请求是谁发起的"。
 *
 * <p><b>只保留一套，这是刻意的：</b>参照实现 {@code SystemTerminator} 里
 * {@code common-web} 与 {@code common-domain} 各有一套 {@code UserInfoContext} /
 * {@code TenantInfoContext}，其中一套还是带 TODO 的空实现 —— 同名不同义的类
 * 会让人反复读错代码，是迁移时必须收敛掉的缺陷。这里只有这一个。
 *
 * <p>字段分两类：
 * <ul>
 *   <li><b>令牌直接带来的</b>：{@link #userId}、{@link #tenantId}、{@link #username}、
 *       {@link #sessionId}（即 jti）、{@link #tokenExpiresAt}；</li>
 *   <li><b>需要回查库得到的</b>：{@link #roleCodes}、{@link #permissions} ——
 *       <b>登录取用、按需装载</b>。不把权限塞进令牌，是因为权限会随授权变更而变，
 *       塞进令牌后撤销就得等令牌过期；放库里查、按请求缓存，改授权即改行为。</li>
 * </ul>
 */
@Getter
@Setter
public class AuthContext {

    private Long userId;

    private Long tenantId;

    private String username;

    private String displayName;

    /** 会话标识（令牌 jti） */
    private String sessionId;

    /** 令牌过期时刻（毫秒） */
    private Long tokenExpiresAt;

    /** 角色编码集合，按需装载 */
    private Set<String> roleCodes = Collections.emptySet();

    /** 权限（资源标识）集合，按需装载 */
    private Set<String> permissions = Collections.emptySet();

    /** 权限是否已装载，避免同一请求内重复回查 */
    private boolean permissionsLoaded;

}
