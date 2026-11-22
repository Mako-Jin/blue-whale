package com.bluewhale.crm.commerce.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.commerce.domain.port.ContractProjection;
import com.bluewhale.crm.commerce.domain.port.ContractSyncPort;
import com.bluewhale.crm.config.CrmContractProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * whale-contract 合同投影内网客户端。
 *
 * <p>CRM 只拉取台账字段（编号 / 金额 / 状态 / 生效与到期），不访问合同正文与附件。</p>
 *
 * @author BlueWhale
 */
@Component
public class ContractInternalClient implements ContractSyncPort {

    /** 按业务查询路径 */
    private static final String BY_BIZ_PATH = "/contract/internal/by-biz?bizType=CRM_CUSTOMER&bizId={bizId}";

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
     * 按合同集成配置构建客户端。
     *
     * @param properties 合同配置
     */
    public ContractInternalClient(CrmContractProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public List<ContractProjection> listByCustomer(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.crm.commerce.customer-id-empty", "客户ID不能为空");
        }
        Long tenantId = TenantContext.get();
        try {
            Map<String, Object> response = restClient.get()
                    .uri(BY_BIZ_PATH, customerId)
                    .header(WebHeaders.TENANT_ID, String.valueOf(tenantId == null ? DEFAULT_TENANT_ID : tenantId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return parse(response);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.crm.commerce.contract-service-unavailable", "合同服务不可用: {0}", exception.getMessage());
        }
    }

    /**
     * 解析统一响应体。
     *
     * @param response 响应体
     * @return 合同投影列表
     */
    @SuppressWarnings("unchecked")
    private List<ContractProjection> parse(Map<String, Object> response) {
        if (response == null) {
            throw failure("空响应");
        }
        Object code = response.get(FIELD_CODE);
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw failure(String.valueOf(response.get(FIELD_MESSAGE)));
        }
        Object data = response.get(FIELD_DATA);
        if (!(data instanceof List<?> rows)) {
            return List.of();
        }
        return rows.stream()
                .filter(Map.class::isInstance)
                .map(row -> toProjection((Map<String, Object>) row))
                .toList();
    }

    /**
     * 行转投影。
     *
     * @param row 响应行
     * @return 合同投影
     */
    private ContractProjection toProjection(Map<String, Object> row) {
        return new ContractProjection(
                toLong(row.get("id")),
                toStringValue(row.get("code")),
                toStringValue(row.get("title")),
                toStringValue(row.get("category")),
                toDecimal(row.get("amount")),
                toStringValue(row.get("currency")),
                toStringValue(row.get("status")),
                toDate(row.get("effectiveDate")),
                toDate(row.get("expireDate")),
                toStringValue(row.get("bizType")),
                toStringValue(row.get("bizId")),
                toLong(row.get("ownerId")),
                toLong(row.get("deptId")),
                toDateTime(row.get("updateTime")));
    }

    /**
     * 构造受控失败异常。
     *
     * @param message 失败原因
     * @return 业务异常
     */
    private BizException failure(String message) {
        return new BizException(ErrorCode.SYSTEM_ERROR, "biz.crm.commerce.failed-contract-projection-sync", "合同投影同步失败: {0}", message);
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

    /**
     * 日期转换（兼容 JSON 数组与 ISO 文本）。
     *
     * @param value 原始值
     * @return 日期；无法解析返回 null
     */
    private LocalDate toDate(Object value) {
        if (value instanceof List<?> parts && parts.size() >= 3) {
            return LocalDate.of(toInt(parts.get(0)), toInt(parts.get(1)), toInt(parts.get(2)));
        }
        String text = toStringValue(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 日期时间转换。
     *
     * @param value 原始值
     * @return 日期时间；无法解析返回 null
     */
    private LocalDateTime toDateTime(Object value) {
        if (value instanceof List<?> parts && parts.size() >= 5) {
            return LocalDateTime.of(toInt(parts.get(0)), toInt(parts.get(1)), toInt(parts.get(2)),
                    toInt(parts.get(3)), toInt(parts.get(4)));
        }
        String text = toStringValue(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 整型转换。
     *
     * @param value 原始值
     * @return 整型
     */
    private int toInt(Object value) {
        return Integer.parseInt(String.valueOf(value));
    }
}
