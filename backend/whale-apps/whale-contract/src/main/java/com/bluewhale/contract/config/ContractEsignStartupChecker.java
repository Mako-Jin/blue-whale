package com.bluewhale.contract.config;

import com.bluewhale.contract.esign.CertificateChainValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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
        validateTsaIfConfigured();
        validateCaIfConfigured();
        validateP12IfConfigured();
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
     * 校验 TSA 配置：开启 {@code tsa-required} 时必须提供合法的 http(s) 地址。
     *
     * <p>不在启动阶段发起 TSA 网络请求（避免依赖启动顺序），但地址格式必须成立；
     * 运行期 TSA 失败由 {@code TimestampService} fail-closed 抛错。</p>
     */
    private void validateTsaIfConfigured() {
        String tsaUrl = properties.getTsaUrl();
        if (!StringUtils.hasText(tsaUrl)) {
            if (properties.isTsaRequired()) {
                throw new IllegalStateException("whale.contract.esign.tsa-required=true 但未配置 tsa-url，"
                        + "拒绝启动。请通过 CONTRACT_ESIGN_TSA_URL 配置 RFC 3161 时间戳服务。");
            }
            log.info("未配置 RFC 3161 时间戳服务（tsa-url），签名不含可信时间戳");
            return;
        }
        try {
            URI uri = URI.create(tsaUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalArgumentException("协议必须为 http/https");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("缺少主机名");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("时间戳服务地址非法: " + tsaUrl + "（" + ex.getMessage() + "）", ex);
        }
        if (!properties.isTsaRequired()) {
            log.warn("已配置 TSA 但 tsa-required=false：验签缺少时间戳不会判定失败，生产建议开启");
        }
        log.info("RFC 3161 时间戳服务配置校验通过: {}", tsaUrl);
    }

    /**
     * 校验 CA 信任根与 CRL 配置；配置信任根时必须能解析出至少一张 CA 证书。
     */
    private void validateCaIfConfigured() {
        if (!properties.isChainValidationEnabled()) {
            if (StringUtils.hasText(properties.getCaCrlPath())) {
                throw new IllegalStateException("配置了 ca-crl-path 但未配置 ca-cert-path，无法校验证书吊销状态");
            }
            log.info("未配置 CA 信任根，签名证书链不做 PKIX 校验（仅适用本地自签）");
            return;
        }
        List<X509Certificate> anchors = new CertificateChainValidator(properties).caCertificates();
        if (StringUtils.hasText(properties.getCaCrlPath())) {
            Path crlPath = Path.of(properties.getCaCrlPath().trim());
            if (!Files.isRegularFile(crlPath) || !Files.isReadable(crlPath)) {
                throw new IllegalStateException("CRL 文件不存在或不可读: " + crlPath);
            }
        }
        log.info("CA 信任根校验通过: {} 张证书，来源 {}", anchors.size(), properties.getCaCertPath());
    }

    /**
     * 校验已配置的 PKCS#12 证书：文件可读、口令可解、含私钥条目、证书未过期、可链到信任根。
     *
     * <p>配置了证书却在启动后才发现无法加载，会造成签署链路在运行期失败并留下半成品记录；
     * 因此在 Bean 装配阶段直接拒绝启动。未配置证书时仅告警（三方渠道由供应商完成签署）。</p>
     */
    private void validateP12IfConfigured() {
        if (!StringUtils.hasText(properties.getP12Path())) {
            log.info("未配置 whale.contract.esign.p12-path，本地签署仅视觉章 + 哈希存证（algorithm=NONE）");
            return;
        }
        Path path = Path.of(properties.getP12Path());
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException("PKCS#12 证书文件不存在或不可读: " + path
                    + "。请检查 whale.contract.esign.p12-path 或 CONTRACT_ESIGN_P12_PATH。");
        }
        char[] password = properties.getP12Password() == null ? new char[0]
                : properties.getP12Password().toCharArray();
        CertificateChainValidator chainValidator = new CertificateChainValidator(properties);
        try (InputStream input = Files.newInputStream(path)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(input, password);
            boolean hasKeyEntry = false;
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (!keyStore.isKeyEntry(alias)) {
                    continue;
                }
                hasKeyEntry = true;
                if (!(keyStore.getCertificate(alias) instanceof X509Certificate certificate)) {
                    throw new IllegalStateException("PKCS#12 条目[" + alias + "]缺少 X.509 证书");
                }
                certificate.checkValidity();
                chainValidator.validate(certificate, certificateChain(keyStore, alias, certificate));
            }
            if (!hasKeyEntry) {
                throw new IllegalStateException("PKCS#12 中未找到含私钥的条目");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("PKCS#12 证书加载失败（口令错误或文件损坏）: "
                    + ex.getMessage(), ex);
        }
        log.info("PKCS#12 证书校验通过: {}", path);
    }

    /**
     * 读取 PKCS#12 条目的证书链（缺失时退化为单证书）。
     *
     * @param keyStore 密钥库
     * @param alias    条目别名
     * @param leaf     签名证书
     * @return 证书链（含签名证书）
     */
    private List<X509Certificate> certificateChain(KeyStore keyStore, String alias, X509Certificate leaf)
            throws KeyStoreException {
        Certificate[] chain = keyStore.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
            return List.of(leaf);
        }
        List<X509Certificate> certificates = new ArrayList<>(chain.length);
        for (Certificate item : chain) {
            if (item instanceof X509Certificate x509) {
                certificates.add(x509);
            }
        }
        return certificates.isEmpty() ? List.of(leaf) : certificates;
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
