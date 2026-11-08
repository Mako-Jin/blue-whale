package com.bluewhale.job.scheduler.handler;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.scheduler.JobExecutionContext;
import com.bluewhale.job.scheduler.JobHandler;
import com.bluewhale.job.service.JobExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通用 HTTP 执行器：调度中心不自带业务逻辑，通过内网 HTTP 触发业务服务内的任务。
 *
 * <p>寻址优先级：</p>
 * <ol>
 *   <li>任务配置了 {@code executor_app + job_code} → 从执行器注册表取在线实例，
 *       与 Nacos 实例列表取交集后按轮询（分片场景按分片号取模）选择；</li>
 *   <li>否则读取执行器参数里的 {@code url} 直连，兼容早期写法与调试。</li>
 * </ol>
 *
 * <p>调用约定（每条都对应一次真实故障）：</p>
 * <ul>
 *   <li>出站请求由 {@link InternalTokenSupport} 自动携带 {@code X-Internal-Token}；</li>
 *   <li>显式携带 {@code X-Tenant-Id}，cron 触发没有用户上下文，对端靠该头建立租户；</li>
 *   <li><b>必须解析响应体业务码</b>：对端是「HTTP 200 + 业务码」，只看 HTTP 状态会把失败记成成功；</li>
 *   <li><b>必须设读超时</b>：调度线程池只有 4 个线程，一个卡死任务会拖垮全部调度。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Slf4j
@Component(HttpJobHandler.BEAN_NAME)
public class HttpJobHandler implements JobHandler {

    /** 执行器 Bean 名称，同时作为 {@code job_info.handler_bean} 的取值 */
    public static final String BEAN_NAME = "httpJobHandler";

    /** 参数键：直连地址 */
    private static final String PARAM_URL = "url";

    /** 参数键：单次读取超时毫秒数 */
    private static final String PARAM_TIMEOUT = "timeoutMillis";

    /** 参数键：请求体 */
    private static final String PARAM_BODY = "body";

    /** 统一响应体中的业务码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体中的数据字段 */
    private static final String FIELD_DATA = "data";

    /** 日志与异常中的响应体截断长度 */
    private static final int MAX_SUMMARY_LENGTH = 1000;

    /** 执行器触发路径模板 */
    private static final String TRIGGER_PATH = "/internal/job/%s/run";

    /** 默认租户ID：任务未配置租户时使用 */
    private static final long DEFAULT_TENANT_ID = 1L;

    private final JobExecutorService jobExecutorService;

    private final JobProperties jobProperties;

    private final ObjectMapper objectMapper;

    private final RestClient.Builder restClientBuilder;

    private final ObjectProvider<DiscoveryClient> discoveryClientProvider;

    /** 轮询计数器：多实例分摊，避免总是打到地址列表的第一个 */
    private final AtomicInteger roundRobin = new AtomicInteger();

    /**
     * 构造执行器。
     *
     * @param jobExecutorService       执行器注册表服务
     * @param jobProperties            任务服务配置
     * @param objectMapper             JSON 序列化器
     * @param restClientBuilderProvider RestClient 构造器（缺失时回退默认构造）
     * @param discoveryClientProvider  服务发现客户端（缺失时只用注册表寻址）
     */
    public HttpJobHandler(JobExecutorService jobExecutorService,
                          JobProperties jobProperties,
                          ObjectMapper objectMapper,
                          ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                          ObjectProvider<DiscoveryClient> discoveryClientProvider) {
        this.jobExecutorService = jobExecutorService;
        this.jobProperties = jobProperties;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
        this.discoveryClientProvider = discoveryClientProvider;
    }

    @Override
    public String execute(String param) throws Exception {
        return executeWithContext(new JobExecutionContext(null, null, param, 0, 1, 0));
    }

    @Override
    public String executeWithContext(JobExecutionContext context) throws Exception {
        JsonNode paramNode = parseParam(context.getParam());
        String url = resolveUrl(context, paramNode);
        int timeoutMillis = resolveTimeout(paramNode);
        String body = resolveBody(paramNode);
        Long tenantId = context.getTenantId() == null ? currentTenant() : context.getTenantId();
        log.info("HTTP 执行器触发: jobId={}, jobCode={}, url={}, timeout={}ms",
                context.getJobId(), context.getJobCode(), url, timeoutMillis);
        return call(url, timeoutMillis, tenantId, body);
    }

    /**
     * 解析执行器参数 JSON。
     *
     * @param param 参数原文
     * @return 参数树；空参数返回空对象
     */
    private JsonNode parseParam(String param) {
        if (!StringUtils.hasText(param)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(param);
        } catch (Exception e) {
            throw new IllegalStateException("执行器参数不是合法 JSON: " + abbreviate(param), e);
        }
    }

    /**
     * 解析目标地址：优先按注册表寻址，其次按参数直连。
     *
     * @param context   执行上下文
     * @param paramNode 执行器参数
     * @return 目标 URL
     */
    private String resolveUrl(JobExecutionContext context, JsonNode paramNode) {
        String executorApp = context.getExecutorApp();
        String jobCode = context.getJobCode();
        if (StringUtils.hasText(executorApp) && StringUtils.hasText(jobCode)) {
            return resolveByRegistry(context.getTenantId(), executorApp, jobCode,
                    context.getShardIndex(), context.getShardTotal());
        }
        String directUrl = paramNode.path(PARAM_URL).asText(null);
        if (StringUtils.hasText(directUrl)) {
            return directUrl;
        }
        throw new IllegalStateException("任务未配置执行器：请填写 executor_app 与 job_code，"
                + "或在执行器参数中提供 url");
    }

    /**
     * 按注册表寻址：在线实例与 Nacos 实例取交集后选择其一。
     *
     * @param tenantId   租户ID
     * @param executorApp 执行器应用名
     * @param jobCode    任务编码
     * @param shardIndex 分片序号
     * @param shardTotal 分片总数
     * @return 目标 URL
     */
    private String resolveByRegistry(Long tenantId, String executorApp, String jobCode,
                                     int shardIndex, int shardTotal) {
        List<String> registered = jobExecutorService.onlineAddresses(tenantId, executorApp, jobCode);
        if (registered.isEmpty()) {
            List<String> anyOnline = jobExecutorService.onlineAddresses(tenantId, executorApp);
            if (!anyOnline.isEmpty()) {
                throw new IllegalStateException("执行器在线但不支持该任务: app=" + executorApp
                        + ", jobCode=" + jobCode
                        + "（检查任务所在 Bean 是否被条件配置关闭，或该版本尚未发布该任务）");
            }
            throw new IllegalStateException("执行器无在线实例: app=" + executorApp
                    + "（检查业务服务是否已开启注册、心跳是否正常）");
        }
        List<String> candidates = intersectWithDiscovery(executorApp, registered);
        String address = pick(candidates, shardIndex, shardTotal);
        return "http://" + address + String.format(TRIGGER_PATH, jobCode);
    }

    /**
     * 与 Nacos 实例列表取交集，过滤掉注册表里残留但已下线的实例。
     *
     * <p>取交集为空时回退到注册表并告警：宁可多一次告警，也不要因为服务发现配置问题
     * 让所有任务直接失败。</p>
     *
     * @param executorApp 执行器应用名
     * @param registered  注册表给出的在线地址
     * @return 可用地址列表
     */
    private List<String> intersectWithDiscovery(String executorApp, List<String> registered) {
        DiscoveryClient discoveryClient = discoveryClientProvider.getIfAvailable();
        if (discoveryClient == null) {
            return registered;
        }
        Set<String> live = new LinkedHashSet<>();
        try {
            for (ServiceInstance instance : discoveryClient.getInstances(executorApp)) {
                live.add(instance.getHost() + ":" + instance.getPort());
            }
        } catch (RuntimeException e) {
            log.warn("服务发现不可用，回退注册表寻址: app={}, reason={}", executorApp, e.getMessage());
            return registered;
        }
        if (live.isEmpty()) {
            log.warn("Nacos 无该服务实例，回退注册表寻址: app={}, registry={}", executorApp, registered);
            return registered;
        }
        List<String> matched = registered.stream().filter(live::contains).toList();
        if (matched.isEmpty()) {
            log.warn("注册表与 Nacos 实例不一致，回退注册表寻址: app={}, registry={}, nacos={}",
                    executorApp, registered, live);
            return registered;
        }
        return matched;
    }

    /**
     * 选择实例：分片场景按分片号取模保证确定性，否则轮询。
     *
     * @param candidates 候选地址
     * @param shardIndex 分片序号
     * @param shardTotal 分片总数
     * @return 选中的地址
     */
    private String pick(List<String> candidates, int shardIndex, int shardTotal) {
        int size = candidates.size();
        if (size == 1) {
            return candidates.get(0);
        }
        int index = shardTotal > 1
                ? Math.floorMod(shardIndex, size)
                : Math.floorMod(roundRobin.getAndIncrement(), size);
        return candidates.get(index);
    }

    /**
     * 解析单次读取超时。
     *
     * @param paramNode 执行器参数
     * @return 超时毫秒数
     */
    private int resolveTimeout(JsonNode paramNode) {
        int configured = paramNode.path(PARAM_TIMEOUT).asInt(0);
        return configured > 0 ? configured : jobProperties.getExecutor().getHttpTimeoutMillis();
    }

    /**
     * 解析请求体。
     *
     * @param paramNode 执行器参数
     * @return 请求体 JSON 文本，默认空对象
     */
    private String resolveBody(JsonNode paramNode) {
        JsonNode body = paramNode.get(PARAM_BODY);
        return body == null || body.isNull() ? "{}" : body.toString();
    }

    /**
     * 当前租户：调度线程已由 {@code JobScheduler} 绑定任务租户，此处兜底默认租户。
     *
     * @return 租户ID
     */
    private Long currentTenant() {
        Long tenant = TenantContext.get();
        return tenant == null ? DEFAULT_TENANT_ID : tenant;
    }

    /**
     * 发起内网调用并校验业务码。
     *
     * @param url          目标地址
     * @param timeoutMillis 读取超时
     * @param tenantId     租户ID
     * @param body         请求体
     * @return 执行结果摘要（响应体 data 或原文）
     */
    private String call(String url, int timeoutMillis, Long tenantId, String body) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(jobProperties.getExecutor().getHttpConnectTimeoutMillis()));
        factory.setReadTimeout(Duration.ofMillis(timeoutMillis));
        RestClient client = restClientBuilder.clone()
                .requestFactory(factory)
                .requestInterceptor(InternalTokenSupport.interceptor())
                .build();
        String responseBody;
        try {
            responseBody = client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            String hint = e.getStatusCode().value() == 404
                    ? "（目标实例不存在该任务，检查 @ScheduledJob 是否已发布）" : "";
            throw new IllegalStateException("执行器调用失败" + hint + ": HTTP "
                    + e.getStatusCode().value() + ", body=" + abbreviate(e.getResponseBodyAsString()), e);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("执行器不可达: " + url + ", reason=" + e.getMessage(), e);
        }
        return assertBusinessSuccess(responseBody);
    }

    /**
     * 校验业务码：对端是「HTTP 200 + 业务码」，非 0 必须视为失败。
     *
     * @param responseBody 响应体
     * @return 结果摘要
     */
    private String assertBusinessSuccess(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "OK";
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            return abbreviate(responseBody);
        }
        JsonNode codeNode = root.get(FIELD_CODE);
        if (codeNode == null || codeNode.isNull()) {
            return abbreviate(responseBody);
        }
        int code = codeNode.asInt(-1);
        if (code != 0) {
            throw new IllegalStateException("业务方返回失败: code=" + code
                    + ", message=" + root.path("message").asText(""));
        }
        JsonNode data = root.get(FIELD_DATA);
        return data == null || data.isNull() ? "OK" : abbreviate(data.toString());
    }

    /**
     * 截断文本，避免超长内容写入日志与执行流水。
     *
     * @param text 原文
     * @return 截断后的文本
     */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_SUMMARY_LENGTH ? text.substring(0, MAX_SUMMARY_LENGTH) : text;
    }
}
