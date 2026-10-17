package com.bluewhale.auth.security;

/**
 * 令牌黑名单：记录已登出但在有效期内仍可被伪造使用的 accessToken。
 * <p>实现须满足：</p>
 * <ul>
 *   <li>分布式生效——多实例共享同一份黑名单（依托 Redis 等共享存储）；</li>
 *   <li>自动过期——TTL 与 token 剩余有效期一致，避免黑名单无限增长；</li>
 *   <li>失败降级——存储不可用时不得阻断登录，查询失败按「未命中」处理。</li>
 * </ul>
 *
 * @author BlueWhale
 */
public interface TokenBlacklist {

    /**
     * 将 token 加入黑名单，过期时间取 token 剩余有效期
     *
     * @param token 已签发的 accessToken 原文
     */
    void add(String token);

    /**
     * 判断 token 是否已在黑名单中
     *
     * @param token 待校验的 accessToken 原文
     * @return {@code true} 表示已登出失效
     */
    boolean contains(String token);
}
