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
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    /** 轮询计数器：多实例分摊，避免总是打到地址列表的第一个 */
    private final AtomicInteger roundRobin = new AtomicInteger();

    /**
     * 构造执行器。
     *
     * @param jobExecutorService       执行器注册表服务（含与 Nacos 的交集解析）
     * @param jobProperties            任务服务配置
     * @param objectMapper             JSON 序列化器
     * @param restClientBuilderProvider RestClient 构造器（缺失时回退默认构造）
     */
    public HttpJobHandler(JobExecutorService jobExecutorService,
                          JobProperties jobProperties,
                          ObjectMapper objectMapper,
                          ObjectProvider<RestClient.Builder> restClientBuilderProvider) {
        this.jobExecutorService = jobExecutorService;
        this.jobProperties = jobProperties;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
    }

    @Override
    public String execute(String param) throws Exception {
        return executeWithContext(new JobExecutionContext(null, null, param, 0, 1, 0));
    }

    @Override
    public String executeWithContext(JobExecutionContext context) throws Exception {
        JsonNode paramNode = parseParam(context.getParam());
        List<DispatchTarget> targets = resolveTargets(context, paramNode);
        int timeoutMillis = resolveTimeout(paramNode);
        String body = resolveBody(paramNode);
        Long tenantId = context.getTenantId() == null ? currentTenant() : context.getTenantId();
        log.info("HTTP 执行器触发: jobId={}, jobCode={}, candidates={}, timeout={}ms",
                context.getJobId(), context.getJobCode(), targets.size(), timeoutMillis);
        return dispatch(context, targets,
                target -> call(target.url(), timeoutMillis, tenantId, body));
    }

    /**
     * 按候选地址逐个派发，**仅**在连接层失败时换下一个候选。
     *
     * <p>为什么需要：容器重建后 IP 会重排，而注册表与 Nacos 两侧都会残留旧地址
     * （注册表靠 90s 心跳判活、Nacos 靠实例摘除延迟），交集过滤拦不住「刚死 30s」的实例。
     * 2026-11-08 实测：容器重建后 8 个任务被派到旧 IP 全部 {@code Connection refused}，
     * 任务各记一次失败——寻址再准也躲不过时序，只能失败即换。</p>
     *
     * <p>为什么**只**在连接层失败时换：连接被拒说明请求没发出去，换实例不会重复执行；
     * 而读超时的请求可能已经在对端跑完，换实例会变成两个实例各跑一遍，
     * 对「推单 / 扣减 / 发通知」类任务就是重复副作用。所以超时一律直接上抛。</p>
     *
     * <p>连接失败的地址会立即摘除（{@code status=0}），下一轮心跳若实例仍活着会自愈回在线，
     * 因此摘除是「临时回避」而不是「判死刑」。</p>
     *
     * @param context 执行上下文
     * @param targets 候选地址（按优先级）
     * @param caller  单次调用
     * @return 执行结果摘要
     */
    String dispatch(JobExecutionContext context, List<DispatchTarget> targets, DispatchCall caller) {
        if (targets.isEmpty()) {
            throw new JobNotDispatchableException("无可用执行器地址: jobCode=" + context.getJobCode());
        }
        ExecutorUnreachableException lastUnreachable = null;
        for (int i = 0; i < targets.size(); i++) {
            DispatchTarget target = targets.get(i);
            context.setResolvedAddress(target.address());
            try {
                return caller.call(target);
            } catch (ExecutorUnreachableException e) {
                lastUnreachable = e;
                int remain = targets.size() - i - 1;
                log.warn("执行器不可达，改派下一候选: jobCode={}, address={}, remain={}, reason={}",
                        context.getJobCode(), target.address(), remain, e.getMessage());
                if (target.address() != null) {
                    String appName = context.getExecutorApp();
                    Long tenantId = context.getTenantId() == null ? currentTenant() : context.getTenantId();
                    jobExecutorService.markUnreachable(tenantId, appName, target.address());
                }
            }
        }
        throw lastUnreachable;
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
     * 解析候选目标：优先按注册表寻址，其次按参数直连。
     *
     * @param context   执行上下文
     * @param paramNode 执行器参数
     * @return 候选地址列表（首个为首选，其余为其备选）
     */
    private List<DispatchTarget> resolveTargets(JobExecutionContext context, JsonNode paramNode) {
        String executorApp = context.getExecutorApp();
        String jobCode = context.getJobCode();
        if (StringUtils.hasText(executorApp) && StringUtils.hasText(jobCode)) {
            return resolveByRegistry(context, executorApp, jobCode);
        }
        String directUrl = paramNode.path(PARAM_URL).asText(null);
        if (StringUtils.hasText(directUrl)) {
            return List.of(new DispatchTarget(null, directUrl));
        }
        throw new JobNotDispatchableException("任务未配置执行器：请填写 executor_app 与 job_code，"
                + "或在执行器参数中提供 url");
    }

    /**
     * 按注册表寻址：在线实例与 Nacos 实例取交集后排序，全部作为候选返回。
     *
     * @param context     执行上下文
     * @param executorApp 执行器应用名
     * @param jobCode     任务编码
     * @return 候选目标列表（首选在前，其余按环形顺序补齐）
     */
    private List<DispatchTarget> resolveByRegistry(JobExecutionContext context, String executorApp, String jobCode) {
        List<String> candidates = jobExecutorService.dispatchableAddresses(
                context.getTenantId(), executorApp, jobCode);
        if (candidates.isEmpty()) {
            List<String> anyOnline = jobExecutorService.onlineAddresses(context.getTenantId(), executorApp);
            if (!anyOnline.isEmpty()) {
                throw new JobNotDispatchableException("执行器在线但不支持该任务: app=" + executorApp
                        + ", jobCode=" + jobCode
                        + "（检查任务所在 Bean 是否被条件配置关闭，或该版本尚未发布该任务）");
            }
            throw new JobNotDispatchableException("执行器无在线实例: app=" + executorApp
                    + "（检查业务服务是否已开启注册、心跳是否正常）");
        }
        String path = String.format(TRIGGER_PATH, jobCode);
        int size = candidates.size();
        int start = pickIndex(size, context.getShardIndex(), context.getShardTotal());
        List<DispatchTarget> targets = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String address = candidates.get((start + i) % size);
            targets.add(new DispatchTarget(address, "http://" + address + path));
        }
        return targets;
    }

    /**
     * 选择首选实例下标：分片场景按分片号取模保证确定性，否则轮询。
     *
     * @param size       候选数量
     * @param shardIndex 分片序号
     * @param shardTotal 分片总数
     * @return 首选下标
     */
    private int pickIndex(int size, int shardIndex, int shardTotal) {
        if (size <= 1) {
            return 0;
        }
        return shardTotal > 1
                ? Math.floorMod(shardIndex, size)
                : Math.floorMod(roundRobin.getAndIncrement(), size);
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
            if (isConnectFailure(e)) {
                throw new ExecutorUnreachableException("执行器不可达: " + url + ", reason=" + e.getMessage(), e);
            }
            throw new IllegalStateException("执行器响应超时，请求可能已在对端执行，不做改派: " + url
                    + ", reason=" + e.getMessage(), e);
        }
        return assertBusinessSuccess(responseBody);
    }

    /**
     * 判定是否为「连接层失败」：这类失败请求一定没有送到对端，可以安全改派。
     *
     * <p>读超时不算——请求可能已经执行完只是响应没回来，改派会导致重复副作用。</p>
     *
     * @param e 访问异常
     * @return 连接被拒 / 主机不可达 / 域名不可解析时返回 {@code true}
     */
    boolean isConnectFailure(ResourceAccessException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ConnectException
                    || cause instanceof NoRouteToHostException
                    || cause instanceof UnknownHostException) {
                return true;
            }
            // 连接阶段超时同样是「一个字节都没发出去」，改派安全。
            // JDK HttpClient 抛 HttpConnectTimeoutException；SimpleClientHttpRequestFactory 抛
            // SocketTimeoutException("Connect timed out")，需按消息区分于读超时("Read timed out")。
            if (cause instanceof HttpConnectTimeoutException) {
                return true;
            }
            if (cause instanceof SocketTimeoutException && isConnectPhaseTimeout(cause.getMessage())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 判断超时异常是否发生在连接阶段（据 JVM 固定英文消息 "Connect timed out"）。
     *
     * @param message 异常消息
     * @return true 表示连接阶段超时
     */
    private boolean isConnectPhaseTimeout(String message) {
        return message != null && message.toLowerCase(Locale.ROOT).contains("connect");
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

    /**
     * 派发候选目标。
     *
     * @param address 执行器地址（{@code host:port}）；按参数直连时为 {@code null}
     * @param url     触发地址
     */
    record DispatchTarget(String address, String url) {
    }

    /**
     * 单次派发调用：把「连接失败即改派」的决策与真实 HTTP 解耦，便于单测覆盖。
     */
    @FunctionalInterface
    interface DispatchCall {

        /**
         * 执行一次派发。
         *
         * @param target 候选目标
         * @return 执行结果摘要
         */
        String call(DispatchTarget target);
    }

    /**
     * 执行器连接失败：请求未送出，可安全改派到其他实例。
     */
    static class ExecutorUnreachableException extends IllegalStateException {

        /**
         * 构造异常。
         *
         * @param message 描述
         * @param cause   原始异常
         */
        ExecutorUnreachableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
