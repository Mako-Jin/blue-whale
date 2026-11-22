package com.bluewhale.pay.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 定时任务单实例锁配置（ShedLock + MySQL）。
 *
 * <p>通知重试与日切对账共用 {@code shedlock} 表：多实例部署时同一时刻只有一个实例执行，
 * 避免 N 个实例每 30 秒同时扫 {@code pay_notify_task}，或同时发起同一业务日的渠道对账。</p>
 *
 * <p>使用数据库时间（{@code usingDbTime}）而不是各实例本地时钟，避免机器时钟漂移造成
 * 提前抢锁或锁不释放。</p>
 *
 * @author BlueWhale
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "5m")
public class PaySchedulerConfig {

    /**
     * 基于 MySQL 的锁提供者。
     *
     * @param dataSource 数据源
     * @return 锁提供者
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
