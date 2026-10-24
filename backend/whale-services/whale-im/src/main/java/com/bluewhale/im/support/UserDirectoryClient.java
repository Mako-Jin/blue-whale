package com.bluewhale.im.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 用户目录客户端：经内网直连 {@code whale-auth} 批量校验用户是否存在且启用。
 *
 * <p>调用 {@code POST /user/internal/exists}（该路径已在网关注册为内网接口，外部访问 403），
 * 地址优先取 {@code whale.im.auth.base-url}，未配置时经 {@link DiscoveryClient} 解析
 * {@code whale-auth} 实例。</p>
 *
 * <p><strong>可用性优先</strong>：校验关闭、无可用实例、网络异常或响应非 0 时统一返回
 * {@link Optional#empty()}，由调用方跳过校验而不是阻断 IM 主流程；仅在成功取得结果时
 * 返回实际存在的用户 ID 集合。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class UserDirectoryClient {

    /** 存在性校验路径 */
    private static final String EXISTS_PATH = "/user/internal/exists";

    /** 响应体业务成功码 */
    private static final int SUCCESS_CODE = 0;

    /** 响应码缺失时的取值 */
    private static final int MISSING_CODE = -1;

    /** 单次校验批量上限（与 whale-auth 入参约束一致） */
    private static final int MAX_BATCH = 200;

    private final RestClient restClient;

    private final DiscoveryClient discoveryClient;

    private final ObjectMapper objectMapper;

    private final String baseUrl;

    private final String serviceName;

    private final boolean validationEnabled;

    /**
     * 构造用户目录客户端。
     *
     * @param discoveryClient      Nacos 服务发现
     * @param objectMapper         JSON 解析器
     * @param baseUrl              显式服务地址（可空，空则走服务发现）
     * @param serviceName          用户目录服务名
     * @param validationEnabled    是否启用成员合法性校验
     * @param timeoutSeconds       建连与读超时（秒）
     */
    public UserDirectoryClient(DiscoveryClient discoveryClient,
                               ObjectMapper objectMapper,
                               @Value("${whale.im.auth.base-url:}") String baseUrl,
                               @Value("${whale.im.auth.service-name:whale-auth}") String serviceName,
                               @Value("${whale.im.member.validation-enabled:true}") boolean validationEnabled,
                               @Value("${whale.im.member.timeout-seconds:3}") long timeoutSeconds) {
        this.discoveryClient = discoveryClient;
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.serviceName = serviceName;
        this.validationEnabled = validationEnabled;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 批量查询「存在且启用」的用户 ID。
     *
     * @param userIds 待校验用户 ID（重复项会被去重）
     * @return 实际存在的用户 ID 集合；无法校验时返回 {@link Optional#empty()}
     */
    public Optional<Set<Long>> existingUserIds(Collection<Long> userIds) {
        if (!validationEnabled || userIds == null || userIds.isEmpty()) {
            return Optional.empty();
        }
        List<Long> batch = new ArrayList<>(new LinkedHashSet<>(userIds));
        if (batch.size() > MAX_BATCH) {
            log.warn("用户合法性校验批量超限，跳过校验: size={}", batch.size());
            return Optional.empty();
        }
        String endpoint = resolveBaseUrl();
        if (endpoint == null) {
            log.warn("用户合法性校验跳过：未找到可用的 {} 实例", serviceName);
            return Optional.empty();
        }
        try {
            String body = restClient.post()
                    .uri(endpoint + EXISTS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("userIds", batch))
                    .retrieve()
                    .body(String.class);
            return parse(body);
        } catch (RuntimeException e) {
            log.warn("用户合法性校验调用失败，已跳过校验: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 返回入参中「不存在或已停用」的用户 ID。
     *
     * <p>无法校验时（关闭 / 无实例 / 异常）返回空列表，调用方据此跳过校验。</p>
     *
     * @param userIds 待校验用户 ID
     * @return 不存在的用户 ID 列表（保持入参顺序）
     */
    public List<Long> missingUserIds(Collection<Long> userIds) {
        Optional<Set<Long>> existing = existingUserIds(userIds);
        if (existing.isEmpty()) {
            return List.of();
        }
        List<Long> missing = new ArrayList<>();
        for (Long userId : new LinkedHashSet<>(userIds)) {
            if (!existing.get().contains(userId)) {
                missing.add(userId);
            }
        }
        return missing;
    }

    /**
     * 解析 {@code Result<List<Long>>} 响应体。
     *
     * @param body 响应 JSON 文本
     * @return 存在的用户 ID 集合；业务码非 0 或解析失败时为空
     */
    private Optional<Set<Long>> parse(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (RuntimeException e) {
            log.warn("用户合法性校验响应解析失败，已跳过校验: {}", e.getMessage());
            return Optional.empty();
        }
        if (root == null || root.path("code").asInt(MISSING_CODE) != SUCCESS_CODE) {
            log.warn("用户合法性校验返回非成功码，已跳过校验");
            return Optional.empty();
        }
        Set<Long> found = new LinkedHashSet<>();
        for (JsonNode node : root.path("data")) {
            found.add(node.asLong());
        }
        return Optional.of(found);
    }

    /**
     * 解析用户目录服务基地址：显式配置优先，否则取服务发现的第一个实例。
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
        ServiceInstance instance = instances.get(0);
        return instance.getUri().toString();
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
}
