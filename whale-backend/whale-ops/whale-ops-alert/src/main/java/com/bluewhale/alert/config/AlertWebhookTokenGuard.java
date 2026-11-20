package com.bluewhale.alert.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Alertmanager 入站令牌启动守卫（审计 P1-2 修复）。
 *
 * <p>{@code whale.alert.notify.webhook-token} 在 application.yml 中有公开源码内的
 * 开发默认值 {@code bluewhale-alert-token}；任何能直连 9103 端口的调用方即可伪造告警注入。</p>
 *
 * <p>守卫采用<b>白名单</b>语义：只有显式激活 {@code dev/local/test} 之一且未同时激活
 * {@code prod} 时，才允许使用默认令牌；未激活任何 profile（生产常见形态）或激活
 * {@code prod} 时命中默认值一律拒绝启动。早期实现是「dev 豁免、其余拒绝」，
 * 一旦生产漏设 {@code SPRING_PROFILES_ACTIVE} 就会以默认 dev profile 启动而失效。</p>
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

    /** 允许使用默认令牌的本地/开发 profile 白名单 */
    private static final Set<String> LOCAL_PROFILES = Set.of("dev", "local", "test");

    /** 生产 profile：与白名单同时存在时按生产处理 */
    private static final String PROD_PROFILE = "prod";

    private final AlertNotifyProperties properties;

    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        if (!DEV_DEFAULT_TOKEN.equals(properties.getWebhookToken())) {
            return;
        }
        List<String> profiles = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .toList();
        boolean local = profiles.stream().anyMatch(LOCAL_PROFILES::contains)
                && !profiles.contains(PROD_PROFILE);
        if (!local) {
            throw new IllegalStateException("Alertmanager 入站令牌仍为默认占位值，非本地开发环境拒绝启动"
                    + "（当前 profiles=" + profiles + "）。"
                    + "请通过环境变量 ALERT_WEBHOOK_TOKEN 设置随机令牌，并同步更新 Alertmanager 的 webhook credentials。");
        }
        log.warn("当前为本地开发环境（profiles={}），允许使用默认 Alertmanager 入站令牌；"
                + "生产环境必须通过环境变量 ALERT_WEBHOOK_TOKEN 覆盖", profiles);
    }
}
