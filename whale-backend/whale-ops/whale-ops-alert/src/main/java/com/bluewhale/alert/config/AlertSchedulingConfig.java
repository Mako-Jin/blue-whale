package com.bluewhale.alert.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 告警模块定时任务开关（记录清理等）。
 *
 * @author BlueWhale
 */
@Configuration
@EnableScheduling
public class AlertSchedulingConfig {
}
