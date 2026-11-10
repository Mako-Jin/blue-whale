package com.bluewhale.twin.support;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.twin.config.TwinProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * internal_api 绑定源客户端：按 bindKey 轮询平台内部只读接口。
 *
 * <p>bindKey 形如 {@code /iot/internal/device/state?deviceId=1}：首段为服务名，
 * 用来解析 base-url（路由表优先，回退 {@code whale.twin.iot.base-url}）；
 * 整条路径必须命中 {@code whale.twin.internal-api.allowed-prefixes} 白名单，
 * 且只发 GET——写操作不在绑定源口径内。</p>
 *
 * <p>响应按平台统一信封 {@code {code, message, data}} 解包，成功返回 data 载荷；
 * <b>失败策略</b>与 {@link IotStateClient} 一致：任何异常只记日志返回 {@code null}，
 * 实时态是渲染旁路，被调服务不可用不能拖垮 3D 场景。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalApiStateClient {

    /** 成功业务码 */
    private static final String CODE_SUCCESS = "0";

    private final TwinProperties properties;

    /**
     * 按 bindKey 拉取内部接口数据（GET + 白名单前置校验）。
     *
     * @param bindKey  绑定键（/服务名/路径?query）
     * @param tenantId 租户ID
     * @return 信封 data 载荷（Map 形态）；失败、不可用或白名单外返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetch(String bindKey, Long tenantId) {
        String path = normalizePath(bindKey);
        if (path == null) {
            return null;
        }
        String baseUrl = resolveBaseUrl(path);
        if (baseUrl == null) {
            return null;
        }
        try {
            Map<String, Object> response = InternalTokenSupport.restClient()
                    .get()
                    .uri(baseUrl + path)
                    .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                log.warn("internal_api 接口返回空响应：bindKey={}", bindKey);
                return null;
            }
            Object code = response.get("code");
            if (code != null && !CODE_SUCCESS.equals(String.valueOf(code))) {
                log.warn("internal_api 接口业务失败：bindKey={}, code={}, message={}",
                        bindKey, code, response.get("message"));
                return null;
            }
            Object data = response.get("data");
            return data instanceof Map ? (Map<String, Object>) data : null;
        } catch (RuntimeException e) {
            log.warn("internal_api 拉取失败（忽略，下个周期重试）：bindKey={}, reason={}",
                    bindKey, e.getMessage());
            return null;
        }
    }

    /**
     * 规范化路径并做形态防护：必须以 / 开头、不含路径穿越与绝对 URL 注入。
     *
     * @param bindKey 绑定键
     * @return 规范化后的路径；非法返回 {@code null}
     */
    public String normalizePath(String bindKey) {
        if (!StringUtils.hasText(bindKey) || !bindKey.startsWith("/")) {
            log.warn("internal_api bindKey 非法（须以 / 开头）：{}", bindKey);
            return null;
        }
        if (bindKey.contains("..") || bindKey.contains("://")) {
            log.warn("internal_api bindKey 含非法片段（路径穿越或绝对 URL）：{}", bindKey);
            return null;
        }
        return bindKey;
    }

    /**
     * 判断 bindKey 是否命中白名单前缀（保存校验与运行时共用同一口径）。
     *
     * @param bindKey 绑定键
     * @return true 允许调用
     */
    public boolean isAllowed(String bindKey) {
        if (!properties.getInternalApi().isEnabled()) {
            return false;
        }
        for (String prefix : properties.getInternalApi().getAllowedPrefixes()) {
            if (StringUtils.hasText(prefix) && bindKey.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按路径首段（服务名）解析 base-url：路由表优先，iot 回退 iot.base-url，其余拒绝。
     *
     * @param path 以 / 开头的接口路径
     * @return base-url；无法解析返回 {@code null}
     */
    String resolveBaseUrl(String path) {
        if (!isAllowed(path)) {
            log.warn("internal_api bindKey 不在白名单内，拒绝调用：{}", path);
            return null;
        }
        String service = path.substring(1).split("/")[0];
        String routed = properties.getInternalApi().getBaseUrls().get(service);
        if (StringUtils.hasText(routed)) {
            return stripTrailingSlash(routed);
        }
        if ("iot".equals(service)) {
            return stripTrailingSlash(properties.getIot().getBaseUrl());
        }
        log.warn("internal_api 服务无 base-url 路由，拒绝调用：service={}", service);
        return null;
    }

    /**
     * 去掉 base-url 末尾斜杠，避免拼接出双斜杠。
     *
     * @param baseUrl 基础地址
     * @return 规范化地址
     */
    private String stripTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
