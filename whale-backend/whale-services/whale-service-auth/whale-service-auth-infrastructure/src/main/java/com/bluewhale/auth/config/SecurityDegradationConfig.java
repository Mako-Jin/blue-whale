package com.bluewhale.auth.config;

import com.bluewhale.security.degradation.SecurityDegradationPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * auth 服务 Redis 降级策略装配（S-02）。
 *
 * <p>与网关共用 {@link SecurityDegradationPolicy}，确保 Redis 不可读时两侧
 * 对「已撤销 / 已命中黑名单」的判定口径一致。</p>
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
