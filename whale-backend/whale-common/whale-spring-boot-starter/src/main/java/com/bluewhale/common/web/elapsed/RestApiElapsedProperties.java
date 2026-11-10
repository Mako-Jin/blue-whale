package com.bluewhale.common.web.elapsed;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 接口耗时日志配置。
 *
 * <p>配置前缀 {@code whale.web.api.elapsed}，默认关闭；开启后对
 * {@code com.bluewhale..controller..} 下的控制器方法打印调用耗时。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = RestApiElapsedProperties.PREFIX)
public class RestApiElapsedProperties {

    /** 配置前缀 */
    public static final String PREFIX = "whale.web.api.elapsed";

    /** 是否启用接口耗时日志 */
    private boolean enabled = false;

    /** 耗时告警阈值（毫秒），超过时以 WARN 级别输出 */
    private long threshold = 1000L;
}
