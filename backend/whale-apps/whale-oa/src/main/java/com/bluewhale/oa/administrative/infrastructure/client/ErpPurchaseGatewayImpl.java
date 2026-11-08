package com.bluewhale.oa.administrative.infrastructure.client;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.administrative.domain.gateway.ErpPurchaseGateway;
import com.bluewhale.oa.administrative.domain.model.ErpPurchaseOrderRequest;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ERP 采购订单内网客户端。
 *
 * <p>统一解析 {@code Result{code,message,data}} 信封；网络/服务不可用转换为
 * {@link OaErrorCode#PURCHASE_ERP_UNAVAILABLE}，由调用方记录失败并支持重试。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpPurchaseGatewayImpl implements ErpPurchaseGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** ERP 服务名 */
    private static final String SERVICE_NAME = "whale-erp";

    /** ERP 采购订单草稿内网路径 */
    private static final String PURCHASE_PATH = "/erp/internal/oa/purchase-order/draft";

    /** 采购订单号字段 */
    private static final String FIELD_PO_NO = "poNo";

    /** 采购订单号备用字段 */
    private static final String FIELD_DOC_NO = "docNo";

    private final DiscoveryClient discoveryClient;

    @Override
    public String createPurchaseOrderDraft(ErpPurchaseOrderRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceType", request.getSourceType());
        body.put("sourceId", request.getSourceId());
        body.put("sourceCode", request.getSourceCode());
        body.put("supplierId", request.getSupplierId());
        body.put("warehouseId", request.getWarehouseId());
        body.put("docDate", request.getDocDate());
        body.put("expectedDate", request.getExpectedDate());
        body.put("currency", request.getCurrency());
        body.put("remark", request.getRemark());
        body.put("lines", request.getLines().stream().map(line -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemCode", line.getItemCode());
            row.put("itemName", line.getItemName());
            row.put("quantity", line.getQuantity());
            row.put("unitPrice", line.getUnitPrice());
            row.put("uom", line.getUom());
            return row;
        }).toList());
        Map<String, Object> response = post(body);
        Object data = requireSuccess(response);
        if (!(data instanceof Map<?, ?> map)) {
            throw new BizException(OaErrorCode.PURCHASE_ERP_UNAVAILABLE, "ERP 采购订单返回数据格式异常");
        }
        Object poNo = map.get(FIELD_PO_NO);
        if (poNo == null) {
            poNo = map.get(FIELD_DOC_NO);
        }
        if (!StringUtils.hasText(poNo == null ? null : String.valueOf(poNo))) {
            throw new BizException(OaErrorCode.PURCHASE_ERP_UNAVAILABLE, "ERP 采购订单未返回单号");
        }
        return String.valueOf(poNo);
    }

    /**
     * 向 ERP 内网提交采购订单草稿请求。
     *
     * @param body 请求体
     * @return ERP 响应信封
     */
    private Map<String, Object> post(Object body) {
        try {
            return restClient().post().uri(baseUrl() + PURCHASE_PATH).headers(this::applyIdentity)
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientResponseException | ResourceAccessException ex) {
            log.warn("ERP 采购订单内网调用失败: {}", ex.getMessage());
            throw OaErrorCode.exception(OaErrorCode.PURCHASE_ERP_UNAVAILABLE);
        }
    }

    /**
     * 将当前请求上下文的租户、用户信息写入内网请求头。
     *
     * @param headers 请求头
     */
    private void applyIdentity(HttpHeaders headers) {
        Long tenantId = RequestContextHolder.getTenantId();
        if (tenantId != null) {
            headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
        }
        Long userId = RequestContextHolder.getUserId();
        if (userId != null) {
            headers.set(WebHeaders.USER_ID, String.valueOf(userId));
        }
        String username = RequestContextHolder.getUsername();
        if (StringUtils.hasText(username)) {
            headers.set(WebHeaders.USERNAME, username);
        }
    }

    /**
     * 校验 ERP 响应信封的业务码并取出数据部分。
     *
     * @param response ERP 响应信封
     * @return 响应中的数据部分
     */
    private Object requireSuccess(Map<String, Object> response) {
        if (response == null) {
            throw OaErrorCode.exception(OaErrorCode.PURCHASE_ERP_UNAVAILABLE);
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            String message = response.get("message") == null ? null : String.valueOf(response.get("message"));
            throw new BizException(OaErrorCode.PURCHASE_ERP_UNAVAILABLE,
                    StringUtils.hasText(message) ? message
                            : OaErrorCode.messageOf(OaErrorCode.PURCHASE_ERP_UNAVAILABLE));
        }
        return response.get("data");
    }

    /**
     * 构建携带内网令牌的 REST 客户端。
     *
     * @return REST 客户端
     */
    private RestClient restClient() {
        return InternalTokenSupport.restClient();
    }

    /**
     * 解析 ERP 服务的实例地址。
     *
     * @return 服务基础地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw OaErrorCode.exception(OaErrorCode.PURCHASE_ERP_UNAVAILABLE);
        }
        return instances.get(0).getUri().toString();
    }
}
