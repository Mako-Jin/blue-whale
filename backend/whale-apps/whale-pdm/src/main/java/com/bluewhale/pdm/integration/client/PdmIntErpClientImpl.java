package com.bluewhale.pdm.integration.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ERP 内网客户端实现
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdmIntErpClientImpl implements PdmIntErpClient {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** ERP 服务名 */
    private static final String SERVICE_NAME = "whale-erp";

    private final DiscoveryClient discoveryClient;

    @Override
    public PdmIntErpItemResult upsertItem(Long tenantId, PdmIntErpItemRequest request) {
        Map<String, Object> response = InternalTokenSupport.restClient()
                .post()
                .uri(baseUrl() + "/erp/internal/item/upsert")
                .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        Map<?, ?> data = requireData(response);
        Long id = data.get("id") instanceof Number number ? number.longValue() : null;
        String code = data.get("code") == null ? request.pdmPartNo() : String.valueOf(data.get("code"));
        boolean created = Boolean.TRUE.equals(data.get("created"));
        log.info("ERP 物料已同步: code={}, id={}, created={}", code, id, created);
        return new PdmIntErpItemResult(id, code, created);
    }

    /**
     * 校验响应并返回 data 字段。
     *
     * @param response ERP 响应
     * @return data 字段
     */
    private Map<?, ?> requireData(Map<String, Object> response) {
        if (response == null) {
            throw failure("ERP 响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw failure(String.valueOf(response.get("message")));
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> map)) {
            throw failure("ERP 响应缺少物料数据");
        }
        return map;
    }

    @Override
    public PdmIntErpItemInfo getItem(Long tenantId, String code) {
        Map<String, Object> response = InternalTokenSupport.restClient()
                .get()
                .uri(baseUrl() + "/erp/internal/item/by-code?code={code}", code)
                .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        verifyReadResponse(response, "物料读取");
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> map)) {
            return new PdmIntErpItemInfo(null, code, null, null, false);
        }
        Long id = map.get("id") instanceof Number number ? number.longValue() : null;
        String itemCode = text(map.get("code"), code);
        return new PdmIntErpItemInfo(id, itemCode, text(map.get("name"), null),
                text(map.get("status"), null), true);
    }

    @Override
    public PdmIntErpStockInfo getStock(Long tenantId, String itemCode) {
        Map<String, Object> response = InternalTokenSupport.restClient()
                .get()
                .uri(baseUrl() + "/erp/internal/stock/by-code?itemCode={itemCode}", itemCode)
                .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        verifyReadResponse(response, "库存读取");
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> map)) {
            throw readFailure("ERP 响应缺少库存数据");
        }
        List<PdmIntErpStockWarehouse> warehouses = new ArrayList<>();
        Object rawWarehouses = map.get("warehouses");
        if (rawWarehouses instanceof List<?> rows) {
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> warehouse)) {
                    continue;
                }
                warehouses.add(new PdmIntErpStockWarehouse(
                        warehouse.get("warehouseId") instanceof Number number ? number.longValue() : null,
                        text(warehouse.get("warehouseCode"), null),
                        text(warehouse.get("warehouseName"), null),
                        decimal(warehouse.get("quantity"))));
            }
        }
        return new PdmIntErpStockInfo(text(map.get("itemCode"), itemCode),
                text(map.get("itemName"), null), decimal(map.get("totalQuantity")), warehouses);
    }

    /**
     * 校验只读响应信封。
     *
     * @param response ERP 响应
     * @param action   操作名
     */
    private void verifyReadResponse(Map<String, Object> response, String action) {
        if (response == null) {
            throw readFailure(action + "响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw readFailure(String.valueOf(response.get("message")));
        }
    }

    /**
     * 取字符串值。
     *
     * @param value       原值
     * @param defaultText 默认值
     * @return 字符串
     */
    private static String text(Object value, String defaultText) {
        return value == null ? defaultText : String.valueOf(value);
    }

    /**
     * 取数值。
     *
     * @param value 原值
     * @return 数值，空值或非法值返回 0
     */
    private static BigDecimal decimal(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 构造只读调用失败异常。
     *
     * @param message 失败原因
     * @return 业务异常
     */
    private BizException readFailure(String message) {
        String safe = StringUtils.hasText(message) ? message : "未知错误";
        return new BizException(ErrorCode.SYSTEM_ERROR, "ERP 库存读取失败: " + safe);
    }

    /**
     * 解析 ERP 服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw failure("ERP 服务不可用");
        }
        return instances.get(0).getUri().toString();
    }

    /**
     * 构造受控失败异常。
     *
     * @param message 失败原因
     * @return 业务异常
     */
    private BizException failure(String message) {
        String safe = StringUtils.hasText(message) ? message : "未知错误";
        return new BizException(ErrorCode.SYSTEM_ERROR, "ERP 物料同步失败: " + safe);
    }
}
