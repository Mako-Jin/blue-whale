package com.bluewhale.auth.application.port;

import com.bluewhale.auth.application.model.social.SocialStateRecord;

import java.util.Optional;

/**
 * 三方授权状态存储端口（权限中心扩展 M2）：{@code state} 载荷的 Redis 化存储。
 *
 * <p>由基础设施适配器实现（{@code StringRedisTemplate} + TTL）。安全语义：
 * state 必须带 TTL（防长期重放）；消费必须一次性（防重复回调）。</p>
 *
 * @author BlueWhale
 */
public interface SocialStateStore {

    /**
     * 保存 state 载荷
     *
     * @param state state 随机串
     * @param record state 业务载荷
     * @param ttlSeconds 有效期（秒）
     */
    void save(String state, SocialStateRecord record, long ttlSeconds);

    /**
     * 读取 state 载荷（不消费）
     *
     * @param state state 随机串
     * @return state 载荷；不存在或已过期返回空
     */
    Optional<SocialStateRecord> load(String state);

    /**
     * 一次性消费 state（原子删除，重复回调第二个到达者失败）
     *
     * @param state state 随机串
     * @return 消费成功返回 {@code true}；不存在或已被消费返回 {@code false}
     */
    boolean consume(String state);
}
