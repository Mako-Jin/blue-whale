package com.bluewhale.mall.order.infrastructure.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.mall.common.MallErrorCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * whale-pay 内网客户端。
 *
 * <p>服务间直连（Nacos 服务发现 + 内网令牌），不经网关；租户上下文以
 * {@code X-Tenant-Id} 头透传。按支付单号幂等：创建已存在返回原单，
 * 取消已取消返回成功。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class PayInternalClient {

    /** 成功业务码 */
    private static final int BIZ_SUCCESS_CODE = 0;

    /** 资源不存在业务码（幂等场景按「无支付单」处理） */
    private static final int BIZ_NOT_FOUND_CODE = 404;

    private final DiscoveryClient discoveryClient;

    private final ObjectMapper objectMapper;

    private final org.springframework.web.client.RestClient restClient;

    private final String serviceName;

    /**
     * 构造内网客户端。
     *
     * @param discoveryClient 服务发现
     * @param objectMapper    JSON
     * @param baseUrl         直连地址（配置了则不经服务发现，用于本机联调）
     * @param serviceName     whale-pay 服务名
     * @param timeoutSeconds  超时秒数
     */
    public PayInternalClient(DiscoveryClient discoveryClient, ObjectMapper objectMapper,
            @Value("${whale.mall.pay.base-url:}") String baseUrl,
            @Value("${whale.mall.pay.service-name:whale-service-pay}") String serviceName,
            @Value("${whale.mall.pay.timeout-seconds:3}") long timeoutSeconds) {
        this.discoveryClient = discoveryClient;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = InternalTokenSupport.builder().requestFactory(factory).build();
        this.baseUrl = StringUtils.hasText(baseUrl) ? trimTrailingSlash(baseUrl) : null;
    }

    /** 直连地址（空表示走服务发现） */
    private final String baseUrl;

    /**
     * 创建支付单（按 orderNo 幂等，已存在返回原单）。
     *
     * @param orderNo  业务单号（商城订单号，作为支付单号）
     * @param amount   应付总额
     * @param currency 币种
     * @param notifyUrl 回调地址（服务名/路径）
     * @return 支付单号（pay_order.orderNo）
     */
    public String createPayOrder(String orderNo, BigDecimal amount, String currency, String notifyUrl) {
        JsonNode data = post("/pay/v1/internal/order", Map.of(
                "orderNo", orderNo,
                "bizType", "MALL_ORDER",
                "amount", amount,
                "currency", currency == null ? "CNY" : currency,
                "channel", "MOCK",
                "notifyUrl", notifyUrl));
        return data.path("orderNo").asText(orderNo);
    }

    /**
     * 发起支付（MOCK 即时成功，成功后 whale-pay 异步回调 mall）。
     *
     * @param orderNo 支付单号
     */
    public void pay(String orderNo) {
        post("/pay/v1/internal/order/" + orderNo + "/pay", Map.of());
    }

    /**
     * 取消支付单（仅待支付可取消；已取消/不存在幂等成功）。
     *
     * @param orderNo 支付单号
     */
    public void cancel(String orderNo) {
        try {
            post("/pay/v1/internal/order/" + orderNo + "/cancel", Map.of());
        } catch (BizException ex) {
            if (ex.getCode() != BIZ_NOT_FOUND_CODE) {
                throw ex;
            }
            log.info("取消订单时支付单不存在，跳过联动取消: orderNo={}", orderNo);
        }
    }

    /**
     * 发起退款（带幂等键）。
     *
     * <p>{@code refundNo} 用售后单号：网络超时后重试带同一个号，支付侧直接返回原退款单，
     * 不会重复出账。整单退款也走这里，不再有「不带号的退款」调用路径。</p>
     *
     * @param orderNo  支付单号
     * @param refundNo 业务退款请求号（幂等键）
     * @param amount   退款金额
     * @param reason   原因
     */
    public void refund(String orderNo, String refundNo, BigDecimal amount, String reason) {
        post("/pay/v1/internal/refund", Map.of(
                "payOrderNo", orderNo,
                "refundNo", refundNo == null ? "" : refundNo,
                "amount", amount,
                "reason", reason == null ? "" : reason));
    }

    /**
     * 发起退款（不带幂等键，仅供不重试场景兜底）。
     *
     * @param orderNo 支付单号
     * @param amount  退款金额
     * @param reason  原因
     */
    public void refund(String orderNo, BigDecimal amount, String reason) {
        refund(orderNo, null, amount, reason);
    }

    /**
     * POST 并解析 Result 包裹的 data。
     *
     * @param path 路径
     * @param body 请求体
     * @return data 节点
     */
    private JsonNode post(String path, Map<String, Object> body) {
        String endpoint = resolveBaseUrl();
        if (endpoint == null) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_PAY_FAILED,
                    "支付服务 " + serviceName + " 实例不可用");
        }
        try {
            String responseBody = restClient.post()
                    .uri(URI.create(endpoint + path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Tenant-Id", String.valueOf(
                            TenantContext.get() == null ? 1L : TenantContext.get()))
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseData(responseBody, path);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("支付内网调用失败: path={}", path, ex);
            throw MallErrorCode.exception(MallErrorCode.ORDER_PAY_FAILED, "支付服务调用失败，请稍后重试");
        }
    }

    /**
     * 解析 Result 包裹体：业务码非 0 转业务异常。
     *
     * @param body 响应体
     * @param path 路径（报错用）
     * @return data 节点
     */
    private JsonNode parseData(String body, String path) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            JsonNode code = root.get("code");
            int bizCode = code == null ? BIZ_SUCCESS_CODE : code.asInt();
            if (bizCode != BIZ_SUCCESS_CODE) {
                throw new BizException(bizCode,
                        root.path("message").asText("支付服务返回错误 code=" + bizCode));
            }
            return root.path("data");
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("支付内网响应解析失败: path={}", path, ex);
            throw MallErrorCode.exception(MallErrorCode.ORDER_PAY_FAILED, "支付服务响应异常");
        }
    }

    /**
     * 解析 whale-pay 基地址：优先配置直连，否则取第一个服务实例。
     *
     * @return 基地址；不可用返回 null
     */
    private String resolveBaseUrl() {
        if (baseUrl != null) {
            return baseUrl;
        }
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        return instances.get(0).getUri().toString();
    }

    /**
     * 去掉末尾斜杠。
     *
     * @param value 地址
     * @return 规范化地址
     */
    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
