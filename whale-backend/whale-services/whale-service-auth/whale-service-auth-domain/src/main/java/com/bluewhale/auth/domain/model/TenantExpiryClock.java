package com.bluewhale.auth.domain.model;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 租户到期判定时钟：统一按 UTC 解释与比较到期时间（S-03）。
 *
 * <p>历史上使用 JVM 默认时区换算 {@code sys_tenant.expire_time}，跨时区部署时会提前或延后判过期。
 * 现在租户到期语义固定为 UTC，应用与网关缓存使用同一口径；存量数据若按本地时区录入，
 * 需在部署前按下发口径统一校准。</p>
 *
 * @author BlueWhale
 */
public final class TenantExpiryClock {

    /** 租户到期时间统一时区 */
    public static final ZoneOffset ZONE = ZoneOffset.UTC;

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private TenantExpiryClock() {
    }

    /**
     * 当前 UTC 时间（用于与 {@code sys_tenant.expire_time} 比较）
     *
     * @return 当前 UTC LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
