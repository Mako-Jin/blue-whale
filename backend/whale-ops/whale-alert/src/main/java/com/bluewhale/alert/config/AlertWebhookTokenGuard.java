package com.bluewhale.alert.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Alertmanager 入站令牌启动守卫（审计 P1-2 修复）。
 *
 * <p>{@code whale.alert.notify.webhook-token} 在 application.yml 中有公开源码内的
 * 开发默认值 {@code bluewhale-alert-token}；非 dev 环境若仍为该默认值，任何能直连
 * 9103 端口的调用方即可伪造告警注入。本守卫在 Bean 装配阶段（早于端口监听）校验，
 * 命中默认值即抛 {@link IllegalStateException} 拒绝启动；dev 环境保持原行为。</p>
 *
 * <p>生产部署通过环境变量 {@code ALERT_WEBHOOK_TOKEN} 注入真实随机令牌。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertWebhookTokenGuard implements InitializingBean {

    /** application.yml 中的开发默认令牌（与 alertmanager-docker.yml 默认配置配套） */
    public static final String DEV_DEFAULT_TOKEN = "bluewhale-alert-token";

    /** 允许使用默认令牌的开发环境 profile 名 */
    private static final String DEV_PROFILE = "dev";

    private final AlertNotifyProperties properties;

    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        if (isDevProfile()) {
            log.warn("当前为 dev 环境，允许使用默认 Alertmanager 入站令牌；"
                    + "生产环境必须通过环境变量 ALERT_WEBHOOK_TOKEN 覆盖");
            return;
        }
        if (DEV_DEFAULT_TOKEN.equals(properties.getWebhookToken())) {
            throw new IllegalStateException("Alertmanager 入站令牌仍为默认占位值，非 dev 环境拒绝启动。"
                    + "请通过环境变量 ALERT_WEBHOOK_TOKEN 设置随机令牌，并同步更新 Alertmanager 的 webhook credentials。");
        }
    }

    /**
     * 判断当前是否激活了 dev profile（大小写不敏感）
     */
    private boolean isDevProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (DEV_PROFILE.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
