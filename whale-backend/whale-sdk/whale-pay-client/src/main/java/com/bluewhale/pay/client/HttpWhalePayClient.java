package com.bluewhale.pay.client;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 基于 {@link RestClient} 的支付内网客户端实现。
 *
 * <p><b>重试口径</b>：仅连接/读超时、HTTP 5xx、服务端 {@code Result.code == 500} 在幂等接口上重试；
 * {@code pay} 非幂等，永不自动重试；{@code PARAM_ERROR}/{@code NOT_FOUND} 等业务错误直接抛出。</p>
 *
 * <p><b>幂等键</b>：{@code create} 缺 {@code orderNo} 时按 {@code bizType-businessKey} 生成；
 * {@code refund} 缺 {@code refundNo} 时生成一次并在本次调用的所有重试中复用。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class HttpWhalePayClient implements WhalePayClient {

    /** 业务错误消息键 */
    private static final String KEY_BIZ_ERROR = "pay.client.biz-error";

    /** 网络异常消息键 */
    private static final String KEY_IO_ERROR = "pay.client.io-error";

    /** HTTP 异常消息键 */
    private static final String KEY_HTTP_ERROR = "pay.client.http-error";

    /** 空响应消息键 */
    private static final String KEY_EMPTY_RESPONSE = "pay.client.empty-response";

    /** 空数据消息键 */
    private static final String KEY_EMPTY_DATA = "pay.client.empty-data";

    /** 参数错误消息键 */
    private static final String KEY_PARAM_ERROR = "pay.client.param-error";

    private final WhalePayClientProperties properties;

    private final RestClient restClient;

    /**
     * 生产构造：使用默认 RestClient.Builder 构建带超时的客户端。
     *
     * @param properties 客户端配置
     */
    public HttpWhalePayClient(WhalePayClientProperties properties) {
        this(properties, RestClient.builder());
    }

    /**
     * 生产构造：沿用宿主应用的 RestClient.Builder（保留其消息转换器与拦截器），再补超时与地址。
     *
     * @param properties 客户端配置
     * @param builder    宿主 RestClient.Builder
     */
    public HttpWhalePayClient(WhalePayClientProperties properties, RestClient.Builder builder) {
        this(properties, configure(properties, builder));
    }

    /**
     * 测试构造：注入已绑定 Mock 服务的 RestClient。
     *
     * @param properties 客户端配置
     * @param restClient 已构建的 RestClient
     */
    HttpWhalePayClient(WhalePayClientProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public PayOrderView create(PayCreateCommand command) {
        PayCreateCommand safe = requireCreate(command);
        CreateBody body = new CreateBody(resolveOrderNo(safe), safe.bizType().trim(), safe.amount(),
                safe.currency(), safe.channel(), safe.notifyUrl().trim(), safe.remark());
        return postOrder(apiPath("/order"), body, true, "创建支付单");
    }

    @Override
    public PayOrderView pay(String orderNo) {
        return postOrder(orderPath(orderNo) + "/pay", null, false, "发起支付");
    }

    @Override
    public PayOrderView cancel(String orderNo) {
        return postOrder(orderPath(orderNo) + "/cancel", null, true, "取消支付单");
    }

    @Override
    public PayOrderView getByOrderNo(String orderNo) {
        String path = orderPath(orderNo);
        return call(() -> restClient.get()
                .uri(path)
                .headers(this::applyHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<Result<PayOrderView>>() {
                }), true, "查询支付单");
    }

    @Override
    public PayRefundView refund(PayRefundCommand command) {
        RefundBody body = requireRefund(command);
        return call(() -> restClient.post()
                .uri(apiPath("/refund"))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(this::applyHeaders)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Result<PayRefundView>>() {
                }), true, "退款");
    }

    /**
     * 发起 POST 并解析支付单响应。
     *
     * @param path      请求路径
     * @param body      请求体（可为空）
     * @param retryable 是否允许自动重试
     * @param action    动作描述（用于错误消息）
     * @return 支付单
     */
    private PayOrderView postOrder(String path, Object body, boolean retryable, String action) {
        return call(() -> {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(path)
                    .headers(this::applyHeaders);
            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            return spec.retrieve().body(new ParameterizedTypeReference<Result<PayOrderView>>() {
            });
        }, retryable, action);
    }

    /**
     * 统一执行与重试：连接异常、5xx、服务端 SYSTEM_ERROR 可重试。
     *
     * @param invocation 单次调用
     * @param retryable  是否允许重试
     * @param action     动作描述
     * @param <T>        返回类型
     * @return 业务数据
     */
    private <T> T call(Supplier<Result<T>> invocation, boolean retryable, String action) {
        int attempts = retryable ? Math.max(properties.getMaxRetries(), 0) + 1 : 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return requireData(invocation.get(), action);
            } catch (ResourceAccessException ex) {
                if (!retryable || attempt == attempts) {
                    throw new PayClientException(ErrorCode.SYSTEM_ERROR.getCode(), KEY_IO_ERROR,
                            action + "网络异常: " + ex.getMessage(), ex);
                }
                log.warn("支付内网调用网络异常，准备重试: action={} attempt={}/{}", action, attempt, attempts);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (!retryable || status < HttpStatus.INTERNAL_SERVER_ERROR.value()
                        || attempt == attempts) {
                    throw new PayClientException(status, KEY_HTTP_ERROR,
                            action + "失败: HTTP " + status, ex);
                }
                log.warn("支付内网调用服务端错误，准备重试: action={} status={} attempt={}/{}",
                        action, status, attempt, attempts);
            } catch (PayClientException ex) {
                if (!retryable || attempt == attempts || ex.getCode() != ErrorCode.SYSTEM_ERROR.getCode()) {
                    throw ex;
                }
                log.warn("支付服务返回系统异常，准备重试: action={} attempt={}/{}", action, attempt, attempts);
            }
        }
        throw new PayClientException(ErrorCode.SYSTEM_ERROR.getCode(), KEY_IO_ERROR, action + "重试次数已用尽");
    }

    /**
     * 解析统一响应体：非 0 抛业务异常，数据为空视为协议错误。
     *
     * @param result 响应
     * @param action 动作描述
     * @param <T>    数据类型
     * @return 业务数据
     */
    private static <T> T requireData(Result<T> result, String action) {
        if (result == null) {
            throw new PayClientException(ErrorCode.SYSTEM_ERROR.getCode(), KEY_EMPTY_RESPONSE,
                    action + "返回空响应");
        }
        if (result.getCode() != ErrorCode.SUCCESS.getCode()) {
            String message = StringUtils.hasText(result.getMessage()) ? result.getMessage() : action + "失败";
            throw new PayClientException(result.getCode(), KEY_BIZ_ERROR, message);
        }
        if (result.getData() == null) {
            throw new PayClientException(ErrorCode.SYSTEM_ERROR.getCode(), KEY_EMPTY_DATA,
                    action + "返回数据为空");
        }
        return result.getData();
    }

    /**
     * 注入内网令牌、租户与链路头。
     *
     * @param headers 请求头
     */
    private void applyHeaders(HttpHeaders headers) {
        String token = resolveToken();
        if (StringUtils.hasText(token)) {
            headers.set(InternalTokenSupport.headerName(), token);
        }
        Long tenantId = resolveTenantId();
        if (tenantId != null) {
            headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
        }
        headers.set(WebHeaders.TRACE_ID, resolveTraceId());
    }

    /**
     * 令牌：显式配置优先，否则取 starter 当前生效令牌。
     *
     * @return 令牌
     */
    private String resolveToken() {
        return StringUtils.hasText(properties.getInternalToken())
                ? properties.getInternalToken().trim() : InternalTokenSupport.token();
    }

    /**
     * 租户：显式配置优先，否则取当前请求租户上下文。
     *
     * @return 租户 ID
     */
    private Long resolveTenantId() {
        return properties.getTenantId() != null ? properties.getTenantId() : TenantContext.get();
    }

    /**
     * 链路 ID：沿用当前 trace，缺失时按网关规格生成 32 位无横线 ID。
     *
     * @return 链路 ID
     */
    private static String resolveTraceId() {
        String traceId = RequestContextHolder.getTraceId();
        return StringUtils.hasText(traceId) ? traceId : UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 构造内网路径（版本段可配置，默认 v1）。
     *
     * @param suffix 内网子路径
     * @return 完整路径
     */
    private String apiPath(String suffix) {
        String version = StringUtils.hasText(properties.getApiVersion())
                ? properties.getApiVersion().trim() : "v1";
        if (!version.startsWith("v")) {
            version = "v" + version;
        }
        return "/pay/" + version + "/internal" + suffix;
    }

    /**
     * 构造单笔支付单路径（单号做路径段编码）。
     *
     * @param orderNo 支付单号
     * @return 完整路径
     */
    private String orderPath(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw paramError("支付单号不能为空");
        }
        return apiPath("/order/" + UriUtils.encodePathSegment(orderNo.trim(), StandardCharsets.UTF_8));
    }

    /**
     * 校验创建命令并返回。
     *
     * @param command 命令
     * @return 原命令
     */
    private static PayCreateCommand requireCreate(PayCreateCommand command) {
        if (command == null) {
            throw paramError("创建命令不能为空");
        }
        requireText(command.bizType(), "bizType 不能为空");
        requireText(command.notifyUrl(), "notifyUrl 不能为空");
        requireAmount(command.amount());
        if (!StringUtils.hasText(command.orderNo()) && !StringUtils.hasText(command.businessKey())) {
            throw paramError("orderNo 与 businessKey 至少传一个");
        }
        return command;
    }

    /**
     * 解析支付单号：显式单号优先，否则按业务类型与业务单号生成。
     *
     * @param command 命令
     * @return 支付单号
     */
    private static String resolveOrderNo(PayCreateCommand command) {
        if (StringUtils.hasText(command.orderNo())) {
            return command.orderNo().trim();
        }
        return command.bizType().trim() + "-" + command.businessKey().trim();
    }

    /**
     * 校验退款命令并补齐幂等键。
     *
     * @param command 命令
     * @return 请求体
     */
    private RefundBody requireRefund(PayRefundCommand command) {
        if (command == null) {
            throw paramError("退款命令不能为空");
        }
        requireText(command.payOrderNo(), "payOrderNo 不能为空");
        requireAmount(command.amount());
        String refundNo = command.refundNo();
        if (!StringUtils.hasText(refundNo)) {
            refundNo = "RF" + UUID.randomUUID().toString().replace("-", "");
            log.info("未传 refundNo，已生成幂等键并在本次调用重试间复用: payOrderNo={} refundNo={}",
                    command.payOrderNo(), refundNo);
        } else {
            refundNo = refundNo.trim();
        }
        return new RefundBody(command.payOrderNo().trim(), refundNo, command.amount(), command.reason());
    }

    /**
     * 校验必填文本
     *
     * @param value 值
     * @param message 缺失提示
     */
    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw paramError(message);
        }
    }

    /**
     * 校验金额为正数
     *
     * @param amount 金额
     */
    private static void requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw paramError("amount 必须大于 0");
        }
    }

    /**
     * 构造参数错误异常
     *
     * @param message 提示
     * @return 异常
     */
    private static PayClientException paramError(String message) {
        return new PayClientException(ErrorCode.PARAM_ERROR.getCode(), KEY_PARAM_ERROR, message);
    }

    /**
     * 按配置补齐 RestClient：基础地址 + 连接/读超时，并构建实例。
     *
     * @param properties 配置
     * @param builder    RestClient.Builder
     * @return RestClient
     */
    private static RestClient configure(WhalePayClientProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return builder.baseUrl(properties.getBaseUrl().trim())
                .requestFactory(factory)
                .build();
    }

    /**
     * 创建请求体
     *
     * @param orderNo    支付单号
     * @param bizType    业务类型
     * @param amount     金额
     * @param currency   币种
     * @param channel    渠道
     * @param notifyUrl  通知地址
     * @param remark     备注
     */
    private record CreateBody(String orderNo, String bizType, BigDecimal amount, String currency,
                              String channel, String notifyUrl, String remark) {
    }

    /**
     * 退款请求体
     *
     * @param payOrderNo 支付单号
     * @param refundNo   退款单号
     * @param amount     金额
     * @param reason     原因
     */
    private record RefundBody(String payOrderNo, String refundNo, BigDecimal amount, String reason) {
    }
}
