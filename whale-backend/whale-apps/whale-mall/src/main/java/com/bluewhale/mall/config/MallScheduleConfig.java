package com.bluewhale.mall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 商城调度开关。
 *
 * <p>独立配置类而不是挂在启动类上：调度是运维口径的开关（未来接 whale-job 托管时
 * 只改这一处），启动类保持只声明「这是个什么应用」。</p>
 *
 * @author BlueWhale
 */
@Configuration
@EnableScheduling
public class MallScheduleConfig {
}
