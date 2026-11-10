package com.bluewhale.oa.drive.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网盘运行参数（配置前缀 {@code whale.oa.drive}）。
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.oa.drive")
public class DriveProperties {

    /** 单用户网盘配额（MB），默认 2048 */
    private long quotaMb = 2048L;
}
