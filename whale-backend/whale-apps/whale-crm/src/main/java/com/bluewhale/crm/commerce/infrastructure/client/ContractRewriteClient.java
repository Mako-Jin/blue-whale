package com.bluewhale.crm.commerce.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.config.CrmContractProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * whale-contract 客户引用重写内网客户端（客户合并 M1-4）。
 *
 * <p>调用 {@code GET /contract/internal/customers/references/count} 与
 * {@code POST /contract/internal/customers/references/rewrite}；
 * 由 {@code whale.crm.contract.enabled} 总开关控制，关闭时调用方跳过合同服务。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class ContractRewriteClient {

    /** 引用计数路径 */
    private static final String COUNT_PATH = "/contract/internal/customers/references/count?bizId={bizId}";

    /** 引用重写路径 */
    private static final String REWRITE_PATH = "/contract/internal/customers/references/rewrite";

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 默认租户 */
    private static final long DEFAULT_TENANT_ID = 1L;

    private final CrmContractProperties properties;

    private final RestClient restClient;

    /**
     * 构造客户端。
     *
     * @param properties 合同服务配置
     */
    public ContractRewriteClient(CrmContractProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 统计源客户被合同服务引用的行数。
     *
     * @param customerId 客户 ID
     * @return 引用合同数；服务不可用时抛业务异常
     */
    public long countReferences(Long customerId) {
        Result<Long> result = execute(() -> restClient.get()
                .uri(COUNT_PATH, customerId)
                .header(WebHeaders.TENANT_ID, tenantHeader())
                .retrieve()
                .body(new ParameterizedTypeReference<Result<Long>>() {
                }));
        return result.getData() == null ? 0L : result.getData();
    }

    /**
     * 把源客户合同引用重写到目标客户。
     *
     * @param sourceId 源客户 ID
     * @param targetId 目标客户 ID
     * @return 受影响行数
     */
    public long rewriteReferences(Long sourceId, Long targetId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceId", sourceId);
        body.put("targetId", targetId);
        Result<Map<String, Object>> result = execute(() -> restClient.post()
                .uri(REWRITE_PATH)
                .header(WebHeaders.TENANT_ID, tenantHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Result<Map<String, Object>>>() {
                }));
        Object rewritten = result.getData() == null ? null : result.getData().get("rewritten");
        if (rewritten instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    /**
     * 统一执行内网调用：补租户头、校验业务码、异常转业务异常。
     *
     * @param call 调用体
     * @param <T> 响应类型
     * @return 响应
     */
    private <T> Result<T> execute(ContractRewriteCall<T> call) {
        Result<T> result;
        try {
            result = call.invoke();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("合同服务引用重写调用失败: error={}", ex.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.crm.commerce.contract-service-unavailable", "合同服务不可用: {0}", ex.getMessage());
        }
        if (result == null || result.getCode() != CODE_SUCCESS) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    result == null ? "合同服务空响应" : result.getMessage());
        }
        return result;
    }

    /**
     * 租户头取值：缺失按默认租户兜底（与 CRM 其它内网客户端一致）。
     *
     * @return 租户头值
     */
    private static String tenantHeader() {
        Long tenantId = TenantContext.get();
        return String.valueOf(tenantId == null ? DEFAULT_TENANT_ID : tenantId);
    }

    /** 内网调用函数式接口（允许抛受检异常）。
     *
     * @param <T> 响应类型
     */
    @FunctionalInterface
    private interface ContractRewriteCall<T> {
        /**
         * 执行调用。
         *
         * @return 响应
         */
        Result<T> invoke();
    }
}
