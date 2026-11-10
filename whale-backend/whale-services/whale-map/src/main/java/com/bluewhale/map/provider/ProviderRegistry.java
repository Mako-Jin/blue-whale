package com.bluewhale.map.provider;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.map.common.MapErrorCode;
import com.bluewhale.map.config.MapProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 供应商注册表：把「能力」翻译成「这一串供应商，按顺序试」。
 *
 * <p><b>路由优先级</b>：能力路由配置 → 默认供应商 → 降级链。去重后逐个尝试，
 * 已配置但未实现该能力的供应商直接跳过（例如只接了地理编码的供应商不该被派去算路径）。</p>
 *
 * <p><b>只做决策不做调用</b>：真正发请求、记配额、落缓存都在业务层，注册表读起来就是一张
 * 静态配置表，改路由不会牵动调用逻辑。</p>
 *
 * @author BlueWhale
 */
@Component
public class ProviderRegistry {

    /** 本地计算标识：出现在能力路由里表示「不调供应商」 */
    public static final String LOCAL_PROVIDER = "local";

    /** 按名索引的供应商 */
    private final Map<String, MapProvider> providers;

    /** 服务配置 */
    private final MapProperties properties;

    /**
     * 按注入的供应商与配置构建注册表
     *
     * @param providerList 容器内的全部供应商实现
     * @param properties   服务配置
     */
    public ProviderRegistry(List<MapProvider> providerList, MapProperties properties) {
        this.properties = properties;
        Map<String, MapProvider> indexed = new LinkedHashMap<>();
        for (MapProvider provider : providerList) {
            indexed.put(provider.name(), provider);
        }
        this.providers = Map.copyOf(indexed);
    }

    /**
     * 某能力的候选供应商链（已过滤不支持与未配置的）
     *
     * @param capability 能力
     * @return 有序候选列表；本地计算能力返回空列表
     */
    public List<MapProvider> chain(MapCapability capability) {
        List<String> names = candidateNames(capability);
        List<MapProvider> chain = new ArrayList<>(names.size());
        for (String name : names) {
            MapProvider provider = providers.get(name);
            if (provider != null && provider.supports(capability) && provider.configured()) {
                chain.add(provider);
            }
        }
        return chain;
    }

    /**
     * 某能力的首选供应商
     *
     * @param capability 能力
     * @return 首选供应商
     * @throws BizException 无任何可用供应商时抛出
     */
    public MapProvider preferred(MapCapability capability) {
        List<MapProvider> chain = chain(capability);
        if (chain.isEmpty()) {
            throw new BizException(MapErrorCode.PROVIDER_UNAVAILABLE,
                    "没有可用的地图供应商（能力 " + capability + "），请检查 Key 与路由配置");
        }
        return chain.get(0);
    }

    /**
     * 判断某能力是否走本地计算
     *
     * @param capability 能力
     * @return 本地计算返回 {@code true}
     */
    public boolean isLocal(MapCapability capability) {
        return LOCAL_PROVIDER.equalsIgnoreCase(routeOf(capability));
    }

    /**
     * 全部已注册供应商（含未配置的，供健康检查展示）
     *
     * @return 供应商名 → 实现
     */
    public Map<String, MapProvider> all() {
        return providers;
    }

    /**
     * 查询某能力配置的供应商名
     *
     * @param capability 能力
     * @return 供应商名（可能为空串）
     */
    public String routeOf(MapCapability capability) {
        String routed = properties.getCapabilityRouting().get(capability.name());
        if (StringUtils.hasText(routed)) {
            return routed.trim();
        }
        return properties.getDefaultProvider();
    }

    /**
     * 计算候选供应商名（未过滤可用性）
     *
     * @param capability 能力
     * @return 去重后的候选名列表
     */
    private List<String> candidateNames(MapCapability capability) {
        Set<String> names = new LinkedHashSet<>();
        String routed = routeOf(capability);
        if (LOCAL_PROVIDER.equalsIgnoreCase(routed)) {
            return List.of();
        }
        if (StringUtils.hasText(routed)) {
            names.add(routed.trim());
        }
        if (StringUtils.hasText(properties.getDefaultProvider())) {
            names.add(properties.getDefaultProvider().trim());
        }
        for (String fallback : properties.getFallback()) {
            if (StringUtils.hasText(fallback)) {
                names.add(fallback.trim());
            }
        }
        names.remove(LOCAL_PROVIDER);
        return new ArrayList<>(names);
    }
}
