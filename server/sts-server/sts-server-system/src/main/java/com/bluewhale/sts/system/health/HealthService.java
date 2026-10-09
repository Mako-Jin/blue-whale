package com.bluewhale.sts.system.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * 健康检查应用服务。
 *
 * <p>当前只汇报进程自身状态。等后续接入数据库、缓存等外部依赖后，
 * 这里应扩展为<b>逐依赖探测并聚合</b>：任一关键依赖不可用即整体降级，
 * 而不是笼统一句"服务正常"——那种健康检查在故障时会继续骗过负载均衡。
 */
@Service
public class HealthService {

    private static final String STATUS_UP = "UP";

    private final String applicationName;
    private final String profile;
    private final Instant startedAt;

    public HealthService(@Value("${spring.application.name:bluewhale-server}") String applicationName,
                         Environment environment) {
        this.applicationName = applicationName;
        this.startedAt = Instant.now();

        String[] activeProfiles = environment.getActiveProfiles();
        this.profile = activeProfiles.length == 0 ? "default" : String.join(",", activeProfiles);
    }

    /**
     * 采集当前健康状态。
     */
    public HealthVO check() {
        return new HealthVO(
                applicationName,
                STATUS_UP,
                profile,
                Duration.between(startedAt, Instant.now()).toMillis(),
                OffsetDateTime.now().toString()
        );
    }

}
