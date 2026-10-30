package com.bluewhale.common.internaltoken;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 内网令牌启动护栏：非 dev 环境未配置令牌时拒绝启动（fail-fast）。
 *
 * <p>策略与 whale-alert {@code AlertWebhookTokenGuard} 对齐：dev 环境允许空令牌并告警，
 * 保证本地与单测不受影响；生产/预发环境若启用校验却未配置令牌，说明运维遗漏，
 * 启动即失败，避免「以为有防护、实际 fail-open」的安全错觉。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class InternalTokenGuard implements InitializingBean {

    /** 允许空令牌的开发环境 profile 名 */
    private static final String DEV_PROFILE = "dev";

    private final InternalTokenProperties properties;

    private final Environment environment;

    /**
     * 构造启动护栏
     *
     * @param properties  内网令牌配置
     * @param environment Spring 环境
     */
    public InternalTokenGuard(InternalTokenProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * 启动时校验：启用校验但令牌为空时，dev 告警、非 dev 拒绝启动
     *
     * @throws IllegalStateException 非 dev 环境令牌为空
     */
    @Override
    public void afterPropertiesSet() {
        if (!properties.isEnabled()) {
            log.warn("服务侧内网令牌校验已通过 bluewhale.internal-token.enabled=false 显式关闭，"
                    + "直连业务端口的 /internal/** 接口仅依赖网络边界防护");
            return;
        }
        if (StringUtils.hasText(properties.resolveToken())) {
            return;
        }
        if (isDevProfile()) {
            log.warn("当前为 dev 环境且未配置内网令牌，服务侧 /internal/** 接口令牌校验未生效；"
                    + "生产环境必须通过配置项 bluewhale.internal-token.token 或环境变量 WHALE_INTERNAL_TOKEN 设置随机令牌");
            return;
        }
        throw new IllegalStateException("内网令牌未配置：非 dev 环境启用服务侧内网令牌校验时，"
                + "必须通过 bluewhale.internal-token.token 或环境变量 WHALE_INTERNAL_TOKEN 设置随机令牌。"
                + "如确认不需要防护，请显式配置 bluewhale.internal-token.enabled=false");
    }

    /**
     * 判断当前是否激活了 dev profile（大小写不敏感）
     *
     * @return 激活 dev profile 返回 {@code true}
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
