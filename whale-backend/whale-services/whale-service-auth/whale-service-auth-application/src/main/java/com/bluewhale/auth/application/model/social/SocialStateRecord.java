package com.bluewhale.auth.application.model.social;

/**
 * 三方授权起始状态（权限中心扩展 M2）：{@code state} 参数背后的业务载荷，由
 * 应用层生成、基础设施层存入 Redis（TTL 到期自动失效）。
 *
 * <p>安全语义：{@code state} 一次性、不可猜测；回调时必须命中且被消费，
 * 防伪造回调与 CSRF。登录意图与绑定意图共用同一结构，以 {@code bindUserId} 区分。</p>
 *
 * @param provider 渠道编码（小写）
 * @param tenantId 目标租户 ID（登录意图必填；绑定意图取当前用户归属租户）
 * @param bindUserId 绑定意图的目标用户 ID；登录意图为 {@code null}
 * @param nonce 随机数（预留 OIDC nonce 扩展，当前渠道不使用）
 * @param redirect 前端回跳相对路径（必须以 {@code /} 开头，防止开放重定向）
 * @param createdAtEpochMs 生成时间（epoch 毫秒，审计用）
 * @author BlueWhale
 */
public record SocialStateRecord(String provider, Long tenantId, Long bindUserId,
                                String nonce, String redirect, long createdAtEpochMs) {

    /**
     * 判断是否绑定意图（方法名避开 is 前缀，防止 Jackson 把派生方法序列化为载荷字段）
     *
     * @return 携带绑定用户 ID 时为绑定意图
     */
    public boolean bindIntent() {
        return bindUserId != null;
    }
}
