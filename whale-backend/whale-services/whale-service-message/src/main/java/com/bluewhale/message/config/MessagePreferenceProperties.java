package com.bluewhale.message.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通知偏好解析配置（Wave 6.17b）。
 *
 * <p>偏好数据集中存放在权限中心（auth {@code sys_user_config}，键形如
 * {@code notify.twin.alert}），message 负责语义解释：静默窗口、最低级别阈值、
 * 渠道过滤。auth 不可用或未配置时一律 fail-open（按默认放行），绝不阻塞投递。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "whale.message.preference")
public class MessagePreferenceProperties {

    /** auth 内网基地址（服务发现直连） */
    private String authBaseUrl = "http://127.0.0.1:8081";

    /** 偏好解析开关；关闭时 resolve 直接按默认放行（不调 auth） */
    private boolean enabled = true;

    /** 配置键前缀（notify 前缀 + 业务类型） */
    private String keyPrefix = "notify.";

    /** 单次解析的用户数上限 */
    private int maxUsers = 200;

    /** auth 调用超时（毫秒） */
    private int timeoutMillis = 3000;
}
