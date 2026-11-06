package com.bluewhale.erp.mfg.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.mfg.domain.model.BomLine;
import com.bluewhale.erp.mfg.domain.model.BomSnapshot;
import com.bluewhale.erp.mfg.domain.port.BomCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PDM BOM 快照内网客户端。
 *
 * <p>调用 {@code GET /pdm/internal/bom/{bomNo}/versions/{versionNo}}；服务不可达、
 * 响应失败、快照空行或行数不一致一律转 {@code MRP_BOM_FETCH_FAILED}，不做降级。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdmBomCatalogImpl implements BomCatalog {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** PDM 服务名 */
    private static final String SERVICE_NAME = "whale-pdm";

    private final DiscoveryClient discoveryClient;

    @Override
    public BomSnapshot fetch(String bomNo, String versionNo) {
        String uri = UriComponentsBuilder
                .fromUriString(baseUrl() + "/pdm/internal/bom/{bomNo}/versions/{versionNo}")
                .buildAndExpand(bomNo, versionNo)
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
            return parse(bomNo, versionNo, response);
        } catch (RestClientException ex) {
            log.warn("PDM BOM 快照拉取失败: bomNo={}, versionNo={}, error={}", bomNo, versionNo, ex.getMessage());
            throw ErpErrorCode.exception(ErpErrorCode.MRP_BOM_FETCH_FAILED,
                    "PDM BOM 快照不可用（" + bomNo + " / " + versionNo + "）：" + ex.getMessage());
        }
    }

    /**
     * 解析响应信封与快照。
     *
     * @param bomNo 请求 BOM 编号
     * @param versionNo 请求版本号
     * @param response 响应
     * @return 快照
     */
    private BomSnapshot parse(String bomNo, String versionNo, Map<?, ?> response) {
        if (response == null) {
            throw ErpErrorCode.exception(ErpErrorCode.MRP_BOM_FETCH_FAILED, "PDM BOM 快照响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw ErpErrorCode.exception(ErpErrorCode.MRP_BOM_FETCH_FAILED,
                    "PDM BOM 快照返回失败：" + response.get("message"));
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> payload)) {
            throw ErpErrorCode.exception(ErpErrorCode.MRP_BOM_FETCH_FAILED, "PDM BOM 快照缺少数据体");
        }
        BomSnapshot snapshot = new BomSnapshot();
        snapshot.setBomNo(text(payload.get("bomNo")) == null ? bomNo : text(payload.get("bomNo")));
        snapshot.setVersionNo(text(payload.get("versionNo")) == null ? versionNo : text(payload.get("versionNo")));
        snapshot.setView(text(payload.get("view")));
        snapshot.setProductCode(text(payload.get("productCode")));
        snapshot.setProductName(text(payload.get("productName")));
        snapshot.setChecksum(text(payload.get("checksum")));
        Object lines = payload.get("lines");
        if (!(lines instanceof List<?> rows) || rows.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.MRP_BOM_FETCH_FAILED,
                    "PDM BOM 快照没有可用行（" + bomNo + " / " + versionNo + "）");
        }
        Integer declaredCount = integer(payload.get("lineCount"));
        if (declaredCount != null && declaredCount != rows.size()) {
            throw ErpErrorCode.exception(ErpErrorCode.MRP_BOM_FETCH_FAILED,
                    "PDM BOM 快照行数不一致：声明 " + declaredCount + "，实际 " + rows.size());
        }
        List<BomLine> parsed = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                parsed.add(toLine(map));
            }
        }
        snapshot.setLines(parsed);
        return snapshot;
    }

    /**
     * 单行转为领域值对象。
     *
     * @param row 原始行
     * @return BOM 行
     */
    private BomLine toLine(Map<?, ?> row) {
        BomLine line = new BomLine();
        line.setPath(text(row.get("path")));
        line.setDepth(integer(row.get("depth")));
        line.setParentPartNo(text(row.get("parentPartNo")));
        line.setChildPartNo(text(row.get("childPartNo")));
        line.setChildPartName(text(row.get("childPartName")));
        line.setQty(decimal(row.get("qty")));
        line.setUom(text(row.get("uom")));
        line.setLossRate(decimal(row.get("lossRate")));
        return line;
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
     * 转整型。
     *
     * @param value 原始值
     * @return 数值；非数字返回 {@code null}
     */
    private Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /**
     * 转小数（经字符串避免浮点精度污染）。
     *
     * @param value 原始值
     * @return 小数；空值或非数字返回 {@code null}
     */
    private BigDecimal decimal(Object value) {
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            log.warn("PDM BOM 快照数值格式非法，按空处理: value={}", text);
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
            throw ErpErrorCode.exception(ErpErrorCode.MRP_BOM_FETCH_FAILED, "PDM 服务不可用");
        }
        return instances.getFirst().getUri().toString();
    }
}
