package com.bluewhale.crm.commerce.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.commerce.domain.port.ErpReceivablePort;
import com.bluewhale.crm.commerce.domain.port.ErpReceivableSummary;
import com.bluewhale.crm.config.CrmArProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * whale-erp 应收只读内网客户端。
 *
 * <p>只读查询未结应收与账龄，不写 ERP 台账。</p>
 *
 * @author BlueWhale
 */
@Component
public class ErpReceivableInternalClient implements ErpReceivablePort {

    /** 按客户查询应收路径 */
    private static final String BY_CUSTOMER_PATH =
            "/erp/internal/finance/receivable/by-customer?customerCode={customerCode}";

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
     * 按应收对账配置构建客户端。
     *
     * @param properties 应收对账配置
     */
    public ErpReceivableInternalClient(CrmArProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public ErpReceivableSummary fetchByCustomerCode(String customerCode) {
        Map<String, Object> response = restClient.get()
                .uri(BY_CUSTOMER_PATH, customerCode)
                .header(WebHeaders.TENANT_ID, tenantHeader())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        return parse(response);
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
     * 解析统一响应体。
     *
     * @param response 响应体
     * @return 应收汇总
     */
    @SuppressWarnings("unchecked")
    private ErpReceivableSummary parse(Map<String, Object> response) {
        if (response == null) {
            throw failure("空响应");
        }
        Object code = response.get(FIELD_CODE);
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw failure(String.valueOf(response.get(FIELD_MESSAGE)));
        }
        Object data = response.get(FIELD_DATA);
        if (!(data instanceof Map<?, ?> map)) {
            return emptySummary();
        }
        Map<String, Object> node = (Map<String, Object>) map;
        Long customerId = toLong(node.get("customerId"));
        if (customerId == null) {
            return emptySummary();
        }
        List<ErpReceivableSummary.ErpReceivableItem> items = new ArrayList<>();
        Object rows = node.get("receivables");
        if (rows instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> item) {
                    items.add(toItem((Map<String, Object>) item));
                }
            }
        }
        return new ErpReceivableSummary(customerId,
                toStringValue(node.get("customerCode")),
                toStringValue(node.get("customerName")),
                toStringValue(node.get("currency")),
                toInteger(node.get("receivableCount")),
                toDecimal(node.get("totalRemaining")),
                toDecimal(node.get("notDue")),
                toDecimal(node.get("overdue1To30")),
                toDecimal(node.get("overdue31To60")),
                toDecimal(node.get("overdue61To90")),
                toDecimal(node.get("overdueOver90")),
                items);
    }

    /**
     * 空摘要。
     *
     * @return 空摘要
     */
    private ErpReceivableSummary emptySummary() {
        return new ErpReceivableSummary(null, null, null, null, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    /**
     * 解析应收明细。
     *
     * @param node 明细节点
     * @return 明细
     */
    private ErpReceivableSummary.ErpReceivableItem toItem(Map<String, Object> node) {
        return new ErpReceivableSummary.ErpReceivableItem(
                toStringValue(node.get("docNo")),
                toDate(node.get("docDate")),
                toDate(node.get("dueDate")),
                toStringValue(node.get("currency")),
                toDecimal(node.get("amount")),
                toDecimal(node.get("settledAmount")),
                toDecimal(node.get("remainingAmount")),
                toStringValue(node.get("status")));
    }

    /**
     * 构造受控失败异常。
     *
     * @param message 失败原因
     * @return 业务异常
     */
    private BizException failure(String message) {
        return new BizException(ErrorCode.SYSTEM_ERROR, "ERP 应收查询失败: " + message);
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
     * 整型转换。
     *
     * @param value 原始值
     * @return 整型；无法解析返回 0
     */
    private Integer toInteger(Object value) {
        String text = toStringValue(value);
        if (text == null) {
            return 0;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /**
     * 金额转换。
     *
     * @param value 原始值
     * @return 金额；无法解析返回 {@link BigDecimal#ZERO}
     */
    private BigDecimal toDecimal(Object value) {
        String text = toStringValue(value);
        if (text == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 日期转换。
     *
     * @param value 原始值
     * @return 日期；无法解析返回 null
     */
    private LocalDate toDate(Object value) {
        String text = toStringValue(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
