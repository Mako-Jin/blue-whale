package com.bluewhale.gateway.config;

import com.bluewhale.security.degradation.SecurityDegradationPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 网关 Redis 降级策略装配（S-02）。
 *
 * <p>把 {@code whale.security.degradation.fail-closed} 注入共享策略，
 * 供撤销、租户状态、令牌黑名单校验在 Redis 不可读时统一取值。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class SecurityDegradationConfig {

    /**
     * 装配降级策略
     *
     * @param failClosed 安全优先开关
     */
    public SecurityDegradationConfig(
            @Value("${whale.security.degradation.fail-closed:false}") boolean failClosed) {
        SecurityDegradationPolicy.configure(failClosed);
    }
}
