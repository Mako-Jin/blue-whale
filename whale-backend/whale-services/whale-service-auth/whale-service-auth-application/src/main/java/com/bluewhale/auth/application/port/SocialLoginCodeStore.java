package com.bluewhale.auth.application.port;

import com.bluewhale.auth.application.model.social.SocialLoginCodeRecord;

import java.util.Optional;

/**
 * 一次性登录码存储端口（权限中心扩展 M2）：三方登录成功后 302 带给前端的短时效凭证。
 *
 * <p>安全语义：一次性（原子消费防重放）、短 TTL（默认 60 秒）、不可猜测（UUID）。
 * 由基础设施适配器以 Redis 实现。</p>
 *
 * @author BlueWhale
 */
public interface SocialLoginCodeStore {

    /**
     * 保存登录码
     *
     * @param loginCode 登录码
     * @param record 载荷
     * @param ttlSeconds 有效期（秒）
     */
    void save(String loginCode, SocialLoginCodeRecord record, long ttlSeconds);

    /**
     * 一次性消费登录码
     *
     * @param loginCode 登录码
     * @return 载荷；不存在、已过期或已被消费返回空
     */
    Optional<SocialLoginCodeRecord> consume(String loginCode);
}
