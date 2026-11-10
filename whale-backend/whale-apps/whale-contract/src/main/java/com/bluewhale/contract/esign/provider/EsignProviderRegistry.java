package com.bluewhale.contract.esign.provider;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.contract.config.ContractEsignProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 电子签渠道注册表：按名称解析渠道实现。
 *
 * <p>新增渠道只需实现 {@link EsignProvider} 并注册为 Spring Bean；
 * 默认渠道由 {@code whale.contract.esign.provider} 决定，切换渠道无需改代码。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class EsignProviderRegistry {

    /** 渠道实现（key 为小写渠道名） */
    private final Map<String, EsignProvider> providers = new LinkedHashMap<>();

    /** 签章配置 */
    private final ContractEsignProperties properties;

    /**
     * 收集全部渠道实现。
     *
     * @param providerList 渠道实现列表
     * @param properties   签章配置
     */
    public EsignProviderRegistry(List<EsignProvider> providerList, ContractEsignProperties properties) {
        this.properties = properties;
        if (providerList != null) {
            for (EsignProvider provider : providerList) {
                providers.put(provider.name().toLowerCase(Locale.ROOT), provider);
            }
        }
        log.info("电子签渠道注册完成: {}", providers.keySet());
    }

    /**
     * 按名称获取渠道，未知渠道抛业务异常。
     *
     * @param name 渠道名（大小写不敏感）
     * @return 渠道实现
     */
    public EsignProvider require(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        EsignProvider provider = providers.get(key);
        if (provider == null) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "不支持的电子签渠道: " + name + "，可选: " + names());
        }
        return provider;
    }

    /**
     * 获取配置的默认渠道。
     *
     * @return 默认渠道实现
     */
    public EsignProvider defaultProvider() {
        String configured = properties.getProvider();
        if (!StringUtils.hasText(configured)) {
            configured = "local";
        }
        return require(configured);
    }

    /**
     * 全部已注册渠道名。
     *
     * @return 渠道名集合
     */
    public Set<String> names() {
        return providers.keySet();
    }
}
