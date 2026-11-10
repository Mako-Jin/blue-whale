package com.bluewhale.twin.support;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.twin.config.TwinProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * whale-iot 实时态客户端：调内网只读接口取设备影子。
 *
 * <p>只访问 {@code /iot/internal/device/state}（网关对 {@code /internal/**}
 * 统一 403），请求经 {@link InternalTokenSupport} 自动附带
 * {@code X-Internal-Token}，并由 {@code X-Tenant-Id} 透传租户。</p>
 *
 * <p><b>失败策略</b>：任何异常只记日志并返回 {@code null}——实时态是渲染旁路，
 * whale-iot 不可用不能拖垮 3D 场景本身。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotStateClient {

    /** 设备实时态路径（whale-iot 内网接口） */
    private static final String STATE_PATH = "/iot/internal/device/state?deviceId=";

    /** 成功业务码 */
    private static final String CODE_SUCCESS = "0";

    private final TwinProperties properties;

    /**
     * 拉取设备实时态（影子快照）。
     *
     * @param deviceId 设备ID
     * @param tenantId 租户ID
     * @return data 载荷（含 online/reported 等）；失败或不可用时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchDeviceState(Long deviceId, Long tenantId) {
        String baseUrl = properties.getIot().getBaseUrl();
        try {
            Map<String, Object> response = InternalTokenSupport.restClient()
                    .get()
                    .uri(baseUrl + STATE_PATH + deviceId)
                    .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                log.warn("设备实时态接口返回空响应：deviceId={}", deviceId);
                return null;
            }
            Object code = response.get("code");
            if (code != null && !CODE_SUCCESS.equals(String.valueOf(code))) {
                log.warn("设备实时态接口业务失败：deviceId={}, code={}, message={}",
                        deviceId, code, response.get("message"));
                return null;
            }
            Object data = response.get("data");
            return data instanceof Map ? (Map<String, Object>) data : null;
        } catch (RuntimeException e) {
            log.warn("设备实时态拉取失败（忽略，下个周期重试）：deviceId={}, reason={}",
                    deviceId, e.getMessage());
            return null;
        }
    }
}
