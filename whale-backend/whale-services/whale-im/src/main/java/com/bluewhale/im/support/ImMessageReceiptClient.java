package com.bluewhale.im.support;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消息服务回执上报客户端：把站内通知的送达/已读状态回传 whale-service-message。
 *
 * <p><b>回执协议</b>：消息服务投递 IM 通知时生成 {@code clientMsgId = msg-<消息ID>-<用户ID>}，
 * 本客户端按同一值作为 {@code providerMessageId} 上报，状态为 {@code DELIVERED} / {@code READ}，
 * {@code receiptId = <clientMsgId>:<状态>} 保证重复上报幂等。</p>
 *
 * <p><b>可用性优先</b>：上报在单线程异步执行，任何异常只记日志，不影响 IM 主流程；
 * 关闭开关、缺少租户、未找到实例时静默跳过。只上报符合 {@code msg-<数字>-<数字>} 形态的
 * 标识，避免把其它调用方的通知误报为消息服务的回执。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class ImMessageReceiptClient {

    /** 回执受理路径（消息服务内网接口） */
    private static final String RECEIPT_PATH = "/message/internal/receipt";

    /** 租户请求头 */
    private static final String HEADER_TENANT = "X-Tenant-Id";

    /** 回执状态：已送达 */
    private static final String STATUS_DELIVERED = "DELIVERED";

    /** 回执状态：已读 */
    private static final String STATUS_READ = "READ";

    /** 回执渠道 */
    private static final String CHANNEL_IM = "IM";

    /** 消息服务生成的幂等键形态 */
    private static final Pattern MESSAGE_SERVICE_ID = Pattern.compile("^msg-(\\d+)-(\\d+)$");

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RestClient restClient;

    private final DiscoveryClient discoveryClient;

    private final String baseUrl;

    private final String serviceName;

    private final boolean enabled;

    /** 单线程异步上报，避免消息服务抖动拖慢 IM 主流程 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "im-message-receipt");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 构造回执上报客户端。
     *
     * @param discoveryClient Nacos 服务发现
     * @param baseUrl         显式服务地址（可空，空则走服务发现）
     * @param serviceName     消息服务名
     * @param enabled         是否启用上报
     * @param timeoutSeconds  建连与读超时（秒）
     */
    public ImMessageReceiptClient(DiscoveryClient discoveryClient,
                                  @Value("${whale.im.message.base-url:}") String baseUrl,
                                  @Value("${whale.im.message.service-name:whale-service-message}") String serviceName,
                                  @Value("${whale.im.message.receipt-enabled:true}") boolean enabled,
                                  @Value("${whale.im.message.timeout-seconds:2}") long timeoutSeconds) {
        this.discoveryClient = discoveryClient;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.serviceName = serviceName;
        this.enabled = enabled;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = InternalTokenSupport.builder().requestFactory(factory).build();
    }

    /**
     * 上报单条通知已送达。
     *
     * @param tenantId    租户ID
     * @param clientMsgId 投递幂等键
     * @param userId      接收人ID
     */
    public void reportDelivered(Long tenantId, String clientMsgId, Long userId) {
        if (!matchesUser(clientMsgId, userId)) {
            return;
        }
        report(tenantId, List.of(receipt(clientMsgId, STATUS_DELIVERED)));
    }

    /**
     * 批量上报通知已读。
     *
     * @param tenantId     租户ID
     * @param clientMsgIds 投递幂等键集合
     */
    public void reportRead(Long tenantId, Collection<String> clientMsgIds) {
        if (clientMsgIds == null || clientMsgIds.isEmpty()) {
            return;
        }
        List<Map<String, Object>> receipts = new ArrayList<>();
        for (String clientMsgId : clientMsgIds) {
            if (isMessageServiceClientId(clientMsgId)) {
                receipts.add(receipt(clientMsgId, STATUS_READ));
            }
        }
        report(tenantId, receipts);
    }

    /**
     * 判断标识是否由消息服务生成（{@code msg-<消息ID>-<用户ID>}）。
     *
     * @param clientMsgId 幂等键
     * @return 是返回 {@code true}
     */
    static boolean isMessageServiceClientId(String clientMsgId) {
        return clientMsgId != null && MESSAGE_SERVICE_ID.matcher(clientMsgId).matches();
    }

    /**
     * 判断标识是否属于指定用户。
     *
     * @param clientMsgId 幂等键
     * @param userId      用户ID
     * @return 是返回 {@code true}
     */
    static boolean matchesUser(String clientMsgId, Long userId) {
        if (clientMsgId == null || userId == null) {
            return false;
        }
        Matcher matcher = MESSAGE_SERVICE_ID.matcher(clientMsgId);
        return matcher.matches() && matcher.group(2).equals(String.valueOf(userId));
    }

    /**
     * 异步提交回执请求。
     *
     * @param tenantId 租户ID
     * @param receipts 回执明细
     */
    private void report(Long tenantId, List<Map<String, Object>> receipts) {
        if (!enabled || tenantId == null || receipts.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("receipts", receipts);
        executor.execute(() -> send(tenantId, body));
    }

    /**
     * 发送回执请求；失败只记日志。
     *
     * @param tenantId 租户ID
     * @param body     请求体
     */
    private void send(Long tenantId, Map<String, Object> body) {
        String endpoint = resolveBaseUrl();
        if (endpoint == null) {
            log.debug("IM 回执上报跳过：未找到可用的 {} 实例", serviceName);
            return;
        }
        try {
            restClient.post()
                    .uri(endpoint + RECEIPT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HEADER_TENANT, String.valueOf(tenantId))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("IM 回执上报失败（忽略）: tenantId={}, error={}", tenantId, e.getMessage());
        }
    }

    /**
     * 构造单条回执。
     *
     * @param clientMsgId 幂等键
     * @param status      回执状态
     * @return 回执明细
     */
    private static Map<String, Object> receipt(String clientMsgId, String status) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("channel", CHANNEL_IM);
        item.put("providerMessageId", clientMsgId);
        item.put("status", status);
        item.put("receiptId", clientMsgId + ":" + status);
        item.put("receiptTime", LocalDateTime.now().format(TIME_FORMAT));
        return item;
    }

    /**
     * 解析消息服务基地址：显式配置优先，否则取服务发现的第一个实例。
     *
     * @return 形如 {@code http://host:port} 的地址；不可用时返回 {@code null}
     */
    private String resolveBaseUrl() {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl;
        }
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        return instances.get(0).getUri().toString();
    }

    /**
     * 去掉末尾斜杠，避免路径拼接出现双斜杠。
     *
     * @param url 原始地址
     * @return 规整后的地址
     */
    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 关闭上报线程池。
     */
    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
