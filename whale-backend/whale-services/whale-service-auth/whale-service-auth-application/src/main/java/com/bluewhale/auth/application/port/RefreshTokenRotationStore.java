package com.bluewhale.auth.application.port;

/**
 * refreshToken 一次性轮换状态存储（AUTH-39 / ADR-001）。
 *
 * <p>承担两件事：</p>
 * <ul>
 *   <li><b>已消费标记</b>——每枚 refreshToken 只能用一次，以 {@code jti} 维度原子抢占；</li>
 *   <li><b>family 吊销标记</b>——检测到复用（同一 refreshToken 被二次使用）即整族吊销，
 *       该 family 下所有 refreshToken 一并失效。</li>
 * </ul>
 *
 * <p><b>降级语义为 fail-closed</b>（ADR-001 R2）：存储不可用时 {@link #markUsed} 返回
 * {@code false}（保守视为「已被消费」）、{@link #isFamilyRevoked} 返回 {@code true}（保守视为
 * 「已吊销」），从而拒绝换发而非放行。此与登出黑名单的「可用性优先、降级放行」语义显式区分，
 * 因该策略仅影响 refresh 路径，不阻断已登录用户正常访问。</p>
 *
 * @author BlueWhale
 */
public interface RefreshTokenRotationStore {

    /**
     * 标记 refreshToken 已消费：以 {@code SET NX} 原子抢占，抢占成功即为首次消费
     *
     * @param jti refreshToken 的 JWT ID
     * @param ttlSeconds 标记存活秒数（取该 refreshToken 剩余有效期）
     * @return {@code true} 表示首次消费；{@code false} 表示已被消费或存储不可用（fail-closed）
     */
    boolean markUsed(String jti, long ttlSeconds);

    /**
     * 查询 family 是否已被吊销
     *
     * @param familyId 令牌 family 标识
     * @return {@code true} 表示该 family 全部 refreshToken 已吊销或存储不可用（fail-closed）
     */
    boolean isFamilyRevoked(String familyId);

    /**
     * 吊销整个 family（复用检测命中时调用）
     *
     * @param familyId 令牌 family 标识
     * @param ttlSeconds 吊销标记存活秒数（取 refreshToken 最长有效期）
     */
    void revokeFamily(String familyId, long ttlSeconds);
}
