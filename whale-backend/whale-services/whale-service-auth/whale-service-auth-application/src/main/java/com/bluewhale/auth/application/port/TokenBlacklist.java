package com.bluewhale.auth.application.port;

/**
 * 令牌黑名单：记录已登出但在有效期内仍可被伪造使用的令牌（accessToken / refreshToken）。
 * <p>实现须满足：</p>
 * <ul>
 *   <li>分布式生效——多实例共享同一份黑名单（依托 Redis 等共享存储）；</li>
 *   <li>自动过期——TTL 与 token 剩余有效期一致，避免黑名单无限增长；</li>
 *   <li>降级语义为 <b>fail-open</b>（可用性优先）——{@link #add} 存储不可用时静默退化
 *       （仅告警，不抛异常，本次登出退化为客户端丢弃 token）；{@link #contains} 查询失败
 *       按「未命中」处理，不阻断登录与鉴权链路。风险口径：黑名单短暂失效只影响已登出
 *       token 的服务端作废，不影响正常用户访问；此与 {@link RefreshTokenRotationStore}
 *       的 fail-closed 语义显式区分。</li>
 * </ul>
 *
 * @author BlueWhale
 */
public interface TokenBlacklist {

    /**
     * 将 token 加入黑名单，过期时间取 token 剩余有效期；存储不可用时静默退化（fail-open）
     *
     * @param token 已签发的 accessToken 或 refreshToken 原文
     */
    void add(String token);

    /**
     * 判断 token 是否已在黑名单中；存储不可用时按「未命中」处理（fail-open）
     *
     * @param token 待校验的 accessToken 或 refreshToken 原文
     * @return {@code true} 表示已登出失效
     */
    boolean contains(String token);
}
