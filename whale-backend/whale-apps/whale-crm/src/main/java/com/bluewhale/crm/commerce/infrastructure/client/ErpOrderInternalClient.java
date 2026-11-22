package com.bluewhale.crm.commerce.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.commerce.domain.port.ErpOrderCommand;
import com.bluewhale.crm.commerce.domain.port.ErpOrderPort;
import com.bluewhale.crm.commerce.domain.port.ErpOrderResult;
import com.bluewhale.crm.config.CrmErpProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * whale-erp 销售订单内网客户端。
 *
 * <p>以 {@code crmOrderNo} 为幂等键；重复下发返回 ERP 已存在的原单。</p>
 *
 * @author BlueWhale
 */
@Component
public class ErpOrderInternalClient implements ErpOrderPort {

    /** 创建订单路径 */
    private static final String CREATE_PATH = "/erp/internal/scm/sales-order";

    /** 按 CRM 单号查询路径 */
    private static final String BY_CRM_ORDER_NO_PATH = "/erp/internal/scm/sales-order/by-crm-order-no?crmOrderNo={crmOrderNo}";

    /** 取消订单路径 */
    private static final String CANCEL_PATH = "/erp/internal/scm/sales-order/{docNo}/cancel";

    /** 统一响应体状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体消息字段 */
    private static final String FIELD_MESSAGE = "message";

    /** 统一响应体数据字段 */
    private static final String FIELD_DATA = "data";

    /** 成功状态码 */
    private static final int CODE_SUCCESS = 0;

    /** 默认租户 ID */
    private static final long DEFAULT_TENANT_ID = 1L;

    private final RestClient restClient;

    /**
     * 按 ERP 集成配置构建客户端。
     *
     * @param properties ERP 配置
     */
    public ErpOrderInternalClient(CrmErpProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public ErpOrderResult create(ErpOrderCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("crmOrderNo", command.getCrmOrderNo());
        body.put("crmCustomerCode", command.getCrmCustomerCode());
        body.put("docDate", command.getDocDate() == null ? null
                : command.getDocDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        body.put("deliveryDate", command.getDeliveryDate() == null ? null
                : command.getDeliveryDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        body.put("currency", command.getCurrency());
        body.put("remark", command.getRemark());
        body.put("lines", toLinePayload(command.getLines()));
        Map<String, Object> response = restClient.post()
                .uri(CREATE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(WebHeaders.TENANT_ID, tenantHeader())
                .body(body).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        return parseResult(response);
    }

    @Override
    public ErpOrderResult findByCrmOrderNo(String crmOrderNo) {
        Map<String, Object> response = restClient.get()
                .uri(BY_CRM_ORDER_NO_PATH, crmOrderNo)
                .header(WebHeaders.TENANT_ID, tenantHeader())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        Map<String, Object> data = requireData(response);
        return data == null ? null : toResult(data);
    }

    @Override
    public void cancel(String erpOrderNo) {
        Map<String, Object> response = restClient.post()
                .uri(CANCEL_PATH, erpOrderNo)
                .contentType(MediaType.APPLICATION_JSON)
                .header(WebHeaders.TENANT_ID, tenantHeader())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        requireSuccess(response);
    }

    /**
     * 当前租户头值。
     *
     * @return 租户 ID 文本
     */
    private String tenantHeader() {
        Long tenantId = TenantContext.get();
        return String.valueOf(tenantId == null ? DEFAULT_TENANT_ID : tenantId);
    }

    /**
     * 分录转下发负载。
     *
     * @param lines 分录
     * @return 负载列表
     */
    private List<Map<String, Object>> toLinePayload(List<ErpOrderCommand.Line> lines) {
        return lines.stream().map(line -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemCode", line.getItemCode());
            row.put("quantity", line.getQuantity());
            row.put("unitPrice", line.getUnitPrice());
            row.put("uom", line.getUom());
            return row;
        }).toList();
    }

    /**
     * 解析创建响应。
     *
     * @param response 响应体
     * @return ERP 履约摘要
     */
    private ErpOrderResult parseResult(Map<String, Object> response) {
        return toResult(requireData(response));
    }

    /**
     * 校验成功并返回数据节点。
     *
     * @param response 响应体
     * @return 数据节点（可能为空）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> requireData(Map<String, Object> response) {
        requireSuccess(response);
        Object data = response.get(FIELD_DATA);
        return data instanceof Map<?, ?> ? (Map<String, Object>) data : null;
    }

    /**
     * 校验统一响应体成功。
     *
     * @param response 响应体
     */
    private void requireSuccess(Map<String, Object> response) {
        if (response == null) {
            throw failure("空响应");
        }
        Object code = response.get(FIELD_CODE);
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw failure(String.valueOf(response.get(FIELD_MESSAGE)));
        }
    }

    /**
     * 数据节点转履约摘要。
     *
     * @param data 数据节点
     * @return 履约摘要
     */
    private ErpOrderResult toResult(Map<String, Object> data) {
        if (data == null) {
            throw failure("响应缺少订单数据");
        }
        return new ErpOrderResult(
                toLong(data.get("id")),
                toStringValue(data.get("docNo")),
                toStringValue(data.get("status")),
                toDecimal(data.get("totalAmount")),
                toDecimal(data.get("shippedAmount")));
    }

    /**
     * 构造受控失败异常。
     *
     * @param message 失败原因
     * @return 业务异常
     */
    private BizException failure(String message) {
        return new BizException(ErrorCode.SYSTEM_ERROR, "biz.crm.commerce.failed-e-r-p-dispatch", "ERP 下发失败: {0}", message);
    }

    /**
     * 文本转换。
     *
     * @param value 原始值
     * @return 文本；空返回 null
     */
    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 长整型转换。
     *
     * @param value 原始值
     * @return 长整型；无法解析返回 null
     */
    private Long toLong(Object value) {
        String text = toStringValue(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 金额转换。
     *
     * @param value 原始值
     * @return 金额；无法解析返回 null
     */
    private BigDecimal toDecimal(Object value) {
        String text = toStringValue(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
