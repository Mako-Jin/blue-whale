package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.auth.application.port.SecurityAlarmPort;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import org.springframework.stereotype.Component;

/**
 * {@link SecurityAlarmPort} 适配器：委托 whale-security 的统一降级告警器
 * （ERROR 日志固定标记 {@code SECURITY_DEGRADATION} + 进程内计数）。
 *
 * @author BlueWhale
 */
@Component
public class SecurityAlarmAdapter implements SecurityAlarmPort {

    @Override
    public void alarm(String scene, String detail, Throwable cause) {
        SecurityDegradationAlerter.alarm(scene, detail, cause);
    }
}
