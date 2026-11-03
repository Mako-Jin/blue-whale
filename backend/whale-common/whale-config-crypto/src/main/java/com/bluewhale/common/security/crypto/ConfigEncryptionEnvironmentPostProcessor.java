package com.bluewhale.common.security.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置密文环境后处理器（REQ-SEC-020）：在配置数据（application.yml / Nacos 拉取前的本地配置）
 * 装载完成后，把所有属性源包装为按需解密的版本。
 *
 * <p>触发条件：{@code whale.config.encryption.enabled=true}（默认）且环境中实际存在
 * {@code ENC(...)} 形式的属性值；仅当存在密文时才强制要求可用密钥，避免无密文的本地开发
 * 因缺少密钥而无法启动。存在密文但缺密钥/密钥非法时直接抛出异常终止启动（fail-fast）。</p>
 *
 * <p>排序：{@link Ordered#LOWEST_PRECEDENCE}，确保晚于 Spring Boot 的
 * {@code ConfigDataEnvironmentPostProcessor}，从而覆盖 yml 中的密文值。
 * Spring Boot 4 起 {@code EnvironmentPostProcessor} 迁至 {@code org.springframework.boot} 包，
 * 通过 {@code @Order} 参与排序（旧 {@code org.springframework.boot.env} 版本已废弃）。</p>
 *
 * @author BlueWhale
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class ConfigEncryptionEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** 日志 */
    private static final Logger LOG = LoggerFactory.getLogger(ConfigEncryptionEnvironmentPostProcessor.class);

    /**
     * Spring Boot 内部属性源名称（{@code ConfigurationPropertySources.ATTACHED_PROPERTY_SOURCE_NAME}）。
     *
     * <p>该属性源由 Boot 的配置数据后处理器创建，其查找会委托到本环境中的原始属性源，
     * 因此包装原始属性源即可让 Binder 读到明文；若把该属性源本身替换为包装类型，
     * {@code ConfigurationPropertySources.get} 的强制类型转换会抛 {@code ClassCastException}。</p>
     */
    private static final String BOOT_CONFIGURATION_PROPERTY_SOURCE = "configurationProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ConfigEncryptionSettings settings = ConfigEncryptionSettings.from(environment);
        if (!settings.isEnabled()) {
            LOG.debug("配置密文解密已关闭（{}={}）", ConfigEncryptionSettings.KEY_ENABLED, false);
            return;
        }
        if (!containsEncryptedValue(environment, settings)) {
            return;
        }
        settings.requireUsableKey();
        ConfigValueCipher cipher = new ConfigValueCipher(settings);
        wrapAll(environment, cipher);
        LOG.info("配置密文解密已启用：ENC(...) 属性读取时自动解密（算法 {}，密钥长度 {}）",
                settings.getAlgorithm(), settings.getPassword().length());
    }

    /**
     * 扫描所有可枚举属性源，判断是否存在 {@code ENC(...)} 属性值
     *
     * @param environment 运行环境
     * @param settings 加密设置（提供前后缀）
     * @return 存在密文返回 true
     */
    private boolean containsEncryptedValue(ConfigurableEnvironment environment, ConfigEncryptionSettings settings) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (isBootInternalSource(source) || !(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                Object value = enumerable.getProperty(name);
                if (value instanceof String text && settings.isEncrypted(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 包装全部属性源：先取名称快照再逐个替换，避免边遍历边替换
     *
     * @param environment 运行环境
     * @param cipher 配置值解密器
     */
    private void wrapAll(ConfigurableEnvironment environment, ConfigValueCipher cipher) {
        MutablePropertySources sources = environment.getPropertySources();
        List<String> names = new ArrayList<>();
        for (PropertySource<?> source : sources) {
            names.add(source.getName());
        }
        for (String name : names) {
            PropertySource<?> source = sources.get(name);
            if (source == null || EncryptedPropertySource.isWrapped(source) || isBootInternalSource(source)) {
                continue;
            }
            sources.replace(name, EncryptedPropertySource.wrap(source, cipher));
        }
    }

    /**
     * 判定是否为 Spring Boot 内部属性源（不得包装，见 {@link #BOOT_CONFIGURATION_PROPERTY_SOURCE}）
     *
     * @param source 待判定属性源
     * @return 内部属性源返回 true
     */
    private boolean isBootInternalSource(PropertySource<?> source) {
        return BOOT_CONFIGURATION_PROPERTY_SOURCE.equals(source.getName());
    }
}
