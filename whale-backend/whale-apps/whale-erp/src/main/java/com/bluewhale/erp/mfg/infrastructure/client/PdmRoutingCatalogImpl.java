package com.bluewhale.erp.mfg.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.mfg.domain.model.RoutingSnapshot;
import com.bluewhale.erp.mfg.domain.model.RoutingStep;
import com.bluewhale.erp.mfg.domain.port.RoutingCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PDM 工艺路线快照内网客户端。
 *
 * <p>调用 {@code GET /pdm/internal/process/routes/by-part/{partCode}}。404 表示无已发布路线，
 * 返回空；服务不可达、响应失败或工序为空转 {@code ROUTING_FETCH_FAILED}。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdmRoutingCatalogImpl implements RoutingCatalog {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** PDM 服务名 */
    private static final String SERVICE_NAME = "whale-pdm";

    private final DiscoveryClient discoveryClient;

    @Override
    public Optional<RoutingSnapshot> latestReleased(String partCode) {
        String uri = UriComponentsBuilder
                .fromUriString(baseUrl() + "/pdm/internal/process/routes/by-part/{partCode}")
                .buildAndExpand(partCode)
                .encode()
                .toUri()
                .toString();
        try {
            Map<?, ?> response = InternalTokenSupport.restClient()
                    .get()
                    .uri(uri)
                    .header(WebHeaders.TENANT_ID, String.valueOf(tenantId()))
                    .retrieve()
                    .body(Map.class);
            return Optional.of(parse(partCode, response));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            log.warn("PDM 工艺路线快照拉取失败: partCode={}, error={}", partCode, ex.getMessage());
            throw ErpErrorCode.exception(ErpErrorCode.ROUTING_FETCH_FAILED,
                    "PDM 工艺路线快照不可用（" + partCode + "）：" + ex.getMessage());
        } catch (RestClientException ex) {
            log.warn("PDM 工艺路线快照拉取失败: partCode={}, error={}", partCode, ex.getMessage());
            throw ErpErrorCode.exception(ErpErrorCode.ROUTING_FETCH_FAILED,
                    "PDM 工艺路线快照不可用（" + partCode + "）：" + ex.getMessage());
        }
    }

    /**
     * 解析响应信封与快照。
     *
     * @param partCode 零件编码
     * @param response 响应
     * @return 快照
     */
    private RoutingSnapshot parse(String partCode, Map<?, ?> response) {
        if (response == null) {
            throw ErpErrorCode.exception(ErpErrorCode.ROUTING_FETCH_FAILED, "PDM 工艺路线快照响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw ErpErrorCode.exception(ErpErrorCode.ROUTING_FETCH_FAILED,
                    "PDM 工艺路线快照返回失败：" + response.get("message"));
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> payload)) {
            throw ErpErrorCode.exception(ErpErrorCode.ROUTING_FETCH_FAILED, "PDM 工艺路线快照缺少数据体");
        }
        RoutingSnapshot snapshot = new RoutingSnapshot();
        snapshot.setRouteNo(text(payload.get("routeNo")));
        snapshot.setVersionNo(text(payload.get("versionNo")));
        snapshot.setPartCode(text(payload.get("partCode")) == null ? partCode : text(payload.get("partCode")));
        snapshot.setPartName(text(payload.get("partName")));
        Object steps = payload.get("steps");
        if (!(steps instanceof List<?> rows) || rows.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.ROUTING_FETCH_FAILED,
                    "PDM 工艺路线快照没有可用工序（" + partCode + "）");
        }
        List<RoutingStep> parsed = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                parsed.add(toStep(map));
            }
        }
        if (parsed.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.ROUTING_FETCH_FAILED,
                    "PDM 工艺路线快照没有可用工序（" + partCode + "）");
        }
        snapshot.setSteps(parsed);
        return snapshot;
    }

    /**
     * 工序行转领域值对象。
     *
     * @param row 原始行
     * @return 工序
     */
    private RoutingStep toStep(Map<?, ?> row) {
        RoutingStep step = new RoutingStep();
        Object stepNo = row.get("stepNo");
        step.setStepNo(stepNo instanceof Number number ? number.intValue() : null);
        step.setName(text(row.get("name")));
        step.setWorkCenterCode(text(row.get("workCenter")));
        step.setStandardHours(decimal(row.get("standardHours")));
        step.setSetupHours(decimal(row.get("setupHours")));
        return step;
    }

    /**
     * 转字符串。
     *
     * @param value 原始值
     * @return 字符串；空值返回 {@code null}
     */
    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 转小数（经字符串避免浮点精度污染）。
     *
     * @param value 原始值
     * @return 小数；空值或非数字返回 {@code null}
     */
    private BigDecimal decimal(Object value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            log.warn("PDM 工艺路线工时格式非法，按空处理: value={}", text);
            return null;
        }
    }

    /**
     * 当前租户。
     *
     * @return 租户 ID
     */
    private Long tenantId() {
        Long tenantId = TenantContext.get();
        return tenantId == null ? TenantContext.DEFAULT_TENANT_ID : tenantId;
    }

    /**
     * 解析 PDM 服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.ROUTING_FETCH_FAILED, "PDM 服务不可用");
        }
        return instances.getFirst().getUri().toString();
    }
}
