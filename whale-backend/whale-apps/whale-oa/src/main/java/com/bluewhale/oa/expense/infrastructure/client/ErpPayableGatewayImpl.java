package com.bluewhale.oa.expense.infrastructure.client;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.expense.domain.gateway.ErpPayableGateway;
import com.bluewhale.oa.expense.domain.model.ErpPayableRequest;
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
 * ERP 应付单内网客户端实现。
 *
 * <p>统一解析 {@code Result{code,message,data}} 信封；网络/服务不可用转换为
 * {@link OaErrorCode#EXPENSE_ERP_UNAVAILABLE}，由调用方记录失败并支持重试。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpPayableGatewayImpl implements ErpPayableGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** ERP 服务名 */
    private static final String SERVICE_NAME = "whale-erp";

    /** ERP 应付单内网路径 */
    private static final String PAYABLE_PATH = "/erp/internal/oa/payable";

    /** 应付单号字段（兼容 code 命名） */
    private static final String FIELD_PAYABLE_NO = "payableNo";

    /** 应付单号备用字段 */
    private static final String FIELD_CODE = "code";

    private final DiscoveryClient discoveryClient;

    @Override
    public String createPayable(ErpPayableRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceType", request.getSourceType());
        body.put("sourceId", request.getSourceId());
        body.put("sourceCode", request.getSourceCode());
        body.put("applicantId", request.getApplicantId());
        body.put("deptId", request.getDeptId());
        body.put("categoryCode", request.getCategoryCode());
        body.put("amount", request.getAmount());
        body.put("currency", request.getCurrency());
        body.put("title", request.getTitle());
        Map<String, Object> response = post(body);
        Object data = requireSuccess(response);
        if (!(data instanceof Map<?, ?> map)) {
            throw new BizException(OaErrorCode.EXPENSE_ERP_UNAVAILABLE, "biz.oa.expense.e-r-p-payable-single-returned", "ERP 应付单返回数据格式异常");
        }
        Object payableNo = map.get(FIELD_PAYABLE_NO);
        if (payableNo == null) {
            payableNo = map.get(FIELD_CODE);
        }
        if (!StringUtils.hasText(payableNo == null ? null : String.valueOf(payableNo))) {
            throw new BizException(OaErrorCode.EXPENSE_ERP_UNAVAILABLE, "biz.oa.expense.e-r-p-payable-single-return", "ERP 应付单未返回单号");
        }
        return String.valueOf(payableNo);
    }

    /**
     * 发起 POST 请求。
     *
     * @param body 请求体
     * @return 响应信封
     */
    private Map<String, Object> post(Object body) {
        try {
            return restClient().post().uri(baseUrl() + PAYABLE_PATH).headers(this::applyIdentity)
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientResponseException | ResourceAccessException ex) {
            log.warn("ERP 应付单内网调用失败: {}", ex.getMessage());
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_ERP_UNAVAILABLE);
        }
    }

    /**
     * 透传租户与当前用户身份请求头。
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
     * 校验业务码并返回 data。
     *
     * @param response 响应信封
     * @return data 字段
     */
    private Object requireSuccess(Map<String, Object> response) {
        if (response == null) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_ERP_UNAVAILABLE);
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            String message = response.get("message") == null ? null : String.valueOf(response.get("message"));
            throw new BizException(OaErrorCode.EXPENSE_ERP_UNAVAILABLE,
                    StringUtils.hasText(message) ? message : OaErrorCode.messageOf(OaErrorCode.EXPENSE_ERP_UNAVAILABLE));
        }
        return response.get("data");
    }

    /**
     * 懒加载内网 RestClient。
     *
     * @return 内网客户端
     */
    private RestClient restClient() {
        return InternalTokenSupport.restClient();
    }

    /**
     * 解析 ERP 服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_ERP_UNAVAILABLE);
        }
        return instances.get(0).getUri().toString();
    }
}
