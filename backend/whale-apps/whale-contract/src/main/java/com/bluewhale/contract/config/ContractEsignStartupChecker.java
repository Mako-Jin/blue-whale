package com.bluewhale.contract.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * 合同电子签生产配置启动校验器（REQ-SEC-010）。
 *
 * <p>非 dev 环境下，若 {@code whale.contract.esign.provider} 为空或仍为 {@code local} 自签，
 * 或回调 HMAC 密钥仍为默认开发值，则直接拒绝启动，避免生产合同使用无第三方效力的自签链路
 * 或可被预测的回调密钥。dev 环境保持原行为以支持本地联调。</p>
 *
 * <p>落地方式选用 {@link InitializingBean}：在 Bean 装配阶段（早于端口监听、早于任何请求）
 * 即完成校验，失败抛出 {@link IllegalStateException} 使应用上下文启动中止。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractEsignStartupChecker implements InitializingBean {

    /** 允许使用 local 自签与默认回调密钥的开发环境 profile 名 */
    private static final String DEV_PROFILE = "dev";

    /** 本地自签渠道名 */
    private static final String LOCAL_PROVIDER = "local";

    /** 覆盖电子签渠道所需的环境变量名 */
    private static final String PROVIDER_ENV_NAME = "CONTRACT_ESIGN_PROVIDER";

    /** 覆盖回调 HMAC 密钥所需的环境变量名 */
    private static final String CALLBACK_SECRET_ENV_NAME = "CONTRACT_ESIGN_CALLBACK_SECRET";

    /** 电子签配置 */
    private final ContractEsignProperties properties;

    /** Spring 环境，用于读取激活 profile */
    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        if (isDevProfile()) {
            log.warn("当前为 dev 环境，允许使用 local 自签与默认回调密钥；"
                    + "生产环境必须通过 {} 与 {} 覆盖", PROVIDER_ENV_NAME, CALLBACK_SECRET_ENV_NAME);
            return;
        }
        String provider = properties.getProvider();
        if (!StringUtils.hasText(provider) || LOCAL_PROVIDER.equalsIgnoreCase(provider.trim())) {
            throw new IllegalStateException("电子签渠道为空或仍为 local 自签，非 dev 环境拒绝启动。"
                    + "请通过环境变量 " + PROVIDER_ENV_NAME + " 配置 esignbao/fadada/ssq 等真实渠道。");
        }
        String callbackSecret = properties.getCallbackSecret();
        if (!StringUtils.hasText(callbackSecret)
                || ContractEsignProperties.DEFAULT_CALLBACK_SECRET.equals(callbackSecret)) {
            throw new IllegalStateException("电子签回调 HMAC 密钥为空或仍为默认开发值，非 dev 环境拒绝启动。"
                    + "请通过环境变量 " + CALLBACK_SECRET_ENV_NAME + " 设置随机密钥。");
        }
        validateProviderConfig(provider.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 校验所选三方渠道的接入参数，避免以空地址/空凭证启动后才在运行时失败。
     *
     * @param provider 渠道名（小写）
     */
    private void validateProviderConfig(String provider) {
        Map<String, ContractEsignProperties.ProviderConfig> providers = properties.getProviders();
        ContractEsignProperties.ProviderConfig config = providers == null ? null : providers.get(provider);
        if (config == null) {
            throw new IllegalStateException("电子签渠道[" + provider + "]未配置 providers."
                    + provider + "，非 dev 环境拒绝启动。");
        }
        if (!StringUtils.hasText(config.getBaseUrl())
                || !StringUtils.hasText(config.getAppId())
                || !StringUtils.hasText(config.getAppSecret())) {
            throw new IllegalStateException("电子签渠道[" + provider + "]缺少 base-url/app-id/app-secret，"
                    + "非 dev 环境拒绝启动。请通过 providers." + provider + " 或对应环境变量补齐。");
        }
    }

    /**
     * 判断当前是否激活了 dev profile（大小写不敏感）。
     *
     * @return 激活的 profile 中含 dev 时返回 {@code true}，否则返回 {@code false}
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
