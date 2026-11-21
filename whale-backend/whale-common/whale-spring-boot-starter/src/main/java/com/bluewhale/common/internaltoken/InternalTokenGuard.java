package com.bluewhale.common.internaltoken;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Map;

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
        validateCallerIdentity();
        if (properties.hasIssuers()) {
            validateIssuers();
            return;
        }
        if (StringUtils.hasText(properties.resolveOutboundToken())) {
            return;
        }
        if (isDevProfile()) {
            log.warn("当前为 dev 环境且未配置内网令牌，服务侧 /internal/** 接口令牌校验未生效；"
                    + "生产环境必须通过 bluewhale.internal-token.token 或环境变量 WHALE_INTERNAL_TOKEN 设置随机令牌");
            return;
        }
        throw new IllegalStateException("内网令牌未配置：非 dev 环境启用服务侧内网令牌校验时，"
                + "必须通过 bluewhale.internal-token.token 或环境变量 WHALE_INTERNAL_TOKEN 设置随机令牌。"
                + "如确认不需要防护，请显式配置 bluewhale.internal-token.enabled=false");
    }

    /**
     * 校验本服务出站身份：issuer-id 与 issuer-secret 必须成对出现，服务 ID 不得包含分隔符
     *
     * @throws IllegalStateException 身份配置不完整或格式非法
     */
    private void validateCallerIdentity() {
        boolean hasId = StringUtils.hasText(properties.getIssuerId());
        boolean hasSecret = StringUtils.hasText(properties.getIssuerSecret());
        if (hasId != hasSecret) {
            throw new IllegalStateException("内网令牌调用方身份配置不完整："
                    + "bluewhale.internal-token.issuer-id 与 issuer-secret 必须同时配置");
        }
        if (hasId && properties.getIssuerId().contains(".")) {
            throw new IllegalStateException("内网令牌服务 ID 不能包含 '.'：令牌格式为 <服务ID>.<密钥>");
        }
    }

    /**
     * 校验按服务签发的令牌清单：密钥非空、服务 ID 不含分隔符、非平台服务必须有租户白名单
     *
     * @throws IllegalStateException 任一条目配置非法
     */
    private void validateIssuers() {
        for (Map.Entry<String, InternalTokenProperties.Issuer> entry : properties.getIssuers().entrySet()) {
            String serviceId = entry.getKey();
            if (!StringUtils.hasText(serviceId) || serviceId.contains(".")) {
                throw new IllegalStateException("内网令牌服务 ID 非法（不能为空或包含 '.'）: " + serviceId);
            }
            InternalTokenProperties.Issuer issuer = entry.getValue();
            if (issuer == null || !StringUtils.hasText(issuer.getSecret())) {
                throw new IllegalStateException("内网令牌服务 " + serviceId + " 未配置 secret");
            }
            if (!issuer.isPlatform() && !hasAnyTenant(issuer)) {
                throw new IllegalStateException("内网令牌服务 " + serviceId
                        + " 既未配置 tenants 也未声明 platform=true，将拒绝其全部租户请求");
            }
        }
    }

    /**
     * 是否配置了任一非空租户
     *
     * @param issuer 服务令牌配置
     * @return 有非空租户返回 {@code true}
     */
    private boolean hasAnyTenant(InternalTokenProperties.Issuer issuer) {
        if (issuer.getTenants() == null) {
            return false;
        }
        for (String tenant : issuer.getTenants()) {
            if (StringUtils.hasText(tenant)) {
                return true;
            }
        }
        return false;
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
