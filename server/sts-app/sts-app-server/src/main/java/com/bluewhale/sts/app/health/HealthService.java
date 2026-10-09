package com.bluewhale.sts.app.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查应用服务。
 *
 * <p><b>逐依赖探测并聚合，而不是笼统一句"服务正常"。</b>只报自身状态的健康检查，
 * 在数据库已经挂掉时依然会返回 UP —— 负载均衡于是继续把流量打进来，
 * 用户看到的是成片的接口报错，而监控大盘上一片绿。这类"会骗人的健康检查"
 * 比没有健康检查更危险：它让人失去了怀疑的方向。
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";

    /** 探活语句：不碰任何业务表，只验证连接可用 */
    private static final String PROBE_SQL = "SELECT 1";

    private final String applicationName;
    private final String profile;
    private final Instant startedAt;
    private final JdbcTemplate jdbcTemplate;

    public HealthService(@Value("${spring.application.name:bluewhale-server}") String applicationName,
                         Environment environment,
                         JdbcTemplate jdbcTemplate) {
        this.applicationName = applicationName;
        this.startedAt = Instant.now();
        this.jdbcTemplate = jdbcTemplate;

        String[] activeProfiles = environment.getActiveProfiles();
        this.profile = activeProfiles.length == 0 ? "default" : String.join(",", activeProfiles);
    }

    /**
     * 采集当前健康状态。
     */
    public HealthVO check() {
        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("db", probeDatabase());

        String overall = dependencies.values().stream().allMatch(STATUS_UP::equals)
                ? STATUS_UP : STATUS_DOWN;

        return new HealthVO(
                applicationName,
                overall,
                profile,
                Duration.between(startedAt, Instant.now()).toMillis(),
                OffsetDateTime.now().toString(),
                dependencies
        );
    }

    /**
     * 探测数据库连通性。
     *
     * <p>异常被吞掉并转成 {@code DOWN}：探活失败本身就是探测结果，
     * 不该让健康检查自身抛异常 —— 那会把"数据库不可用"变成"健康检查接口 500"，
     * 让调用方以为整个服务已经没救了。
     */
    private String probeDatabase() {
        try {
            Integer result = jdbcTemplate.queryForObject(PROBE_SQL, Integer.class);
            return result != null ? STATUS_UP : STATUS_DOWN;
        } catch (Exception ex) {
            log.warn("数据库探活失败 | message={}", ex.getMessage());
            return STATUS_DOWN;
        }
    }

}
