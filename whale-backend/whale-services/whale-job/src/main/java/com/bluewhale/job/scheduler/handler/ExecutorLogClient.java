package com.bluewhale.job.scheduler.handler;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.service.JobExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 执行器日志回捞客户端：按实例地址读取执行器本地保存的「最近一次执行日志」。
 *
 * <p>为什么不复用 {@link HttpJobHandler}：那个是「触发并等待执行」的同步调用，超时取任务级配置；
 * 回捞是只读的旁路查询，超时要短、失败要软（拿不到日志不该让管理端报错）。</p>
 *
 * <p>寻址顺序：优先用 {@code job_log.executor_address}（本次执行实际打的实例）——
 * 执行日志写在执行器本地，多副本时只有那一台有文件；地址缺失（旧记录、直连模式）时
 * 退化为按注册表任选一个在线实例，并如实标注可能取到别的实例的日志。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorLogClient {

    /** 回捞路径模板 */
    private static final String LOG_PATH = "/internal/job/%s/log/latest";

    /** 默认租户ID：任务未配置租户时使用 */
    private static final long DEFAULT_TENANT_ID = 1L;

    /** 统一响应体中的业务码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体中的数据字段 */
    private static final String FIELD_DATA = "data";

    /** 统一响应体中的消息字段 */
    private static final String FIELD_MESSAGE = "message";

    /** 回捞请求的超时上限：旁路查询不该拖住管理端页面 */
    private static final int MAX_TIMEOUT_MILLIS = 5000;

    private final JobExecutorService jobExecutorService;

    private final JobProperties jobProperties;

    private final ObjectMapper objectMapper;

    private final ObjectProvider<RestClient.Builder> restClientBuilderProvider;

    /**
     * 回捞结果。
     *
     * @param address 实际请求的地址
     * @param content 日志正文，未取到为 {@code null}
     * @param note    未取到时的原因
     */
    public record Fetched(String address, String content, String note) {

        /**
         * 是否取到日志。
         *
         * @return 取到返回 {@code true}
         */
        public boolean found() {
            return content != null;
        }
    }

    /**
     * 回捞最近一次执行日志。
     *
     * @param tenantId         租户ID
     * @param executorApp      执行器应用名
     * @param jobCode          任务编码
     * @param preferredAddress 本次执行实际派发到的地址（可为空）
     * @return 回捞结果，永不抛异常
     */
    public Fetched fetch(Long tenantId, String executorApp, String jobCode, String preferredAddress) {
        if (!StringUtils.hasText(jobCode)) {
            return new Fetched(null, null, "任务未配置 job_code，无法回捞（按参数直连模式无本地日志）");
        }
        List<String> candidates = resolveCandidates(tenantId, executorApp, jobCode, preferredAddress);
        if (candidates.isEmpty()) {
            return new Fetched(null, null, "执行器无在线实例: app=" + executorApp);
        }
        String lastNote = null;
        for (String address : candidates) {
            Fetched fetched = tryFetch(address, jobCode, tenantId);
            if (fetched.found()) {
                return fetched;
            }
            lastNote = fetched.note();
        }
        return new Fetched(candidates.get(0), null, lastNote);
    }

    /**
     * 解析候选地址：优先执行流水里记录的地址，其次按注册表。
     *
     * @param tenantId         租户ID
     * @param executorApp      执行器应用名
     * @param jobCode          任务编码
     * @param preferredAddress 记录的地址
     * @return 候选地址列表
     */
    private List<String> resolveCandidates(Long tenantId, String executorApp, String jobCode,
                                           String preferredAddress) {
        if (StringUtils.hasText(preferredAddress)) {
            return List.of(preferredAddress);
        }
        if (!StringUtils.hasText(executorApp)) {
            return List.of();
        }
        return jobExecutorService.dispatchableAddresses(tenantId, executorApp, jobCode);
    }

    /**
     * 向单个实例请求日志。
     *
     * @param address 实例地址
     * @param jobCode 任务编码
     * @param tenantId 租户ID
     * @return 回捞结果
     */
    private Fetched tryFetch(String address, String jobCode, Long tenantId) {
        String url = "http://" + address + String.format(LOG_PATH, jobCode);
        try {
            String body = client().get()
                    .uri(url)
                    .header(WebHeaders.TENANT_ID, String.valueOf(tenantId == null ? DEFAULT_TENANT_ID : tenantId))
                    .retrieve()
                    .body(String.class);
            return parse(address, body);
        } catch (RestClientResponseException e) {
            log.debug("执行日志回捞失败: url={}, status={}", url, e.getStatusCode().value());
            return new Fetched(address, null, "实例返回 HTTP " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            log.debug("执行日志回捞异常: url={}, reason={}", url, e.getMessage());
            return new Fetched(address, null, "实例不可达: " + e.getMessage());
        }
    }

    /**
     * 解析统一响应体。
     *
     * @param address 实例地址
     * @param body    响应体
     * @return 回捞结果
     */
    private Fetched parse(String address, String body) {
        if (!StringUtils.hasText(body)) {
            return new Fetched(address, null, "实例返回空响应");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (RuntimeException e) {
            return new Fetched(address, null, "响应不是合法 JSON");
        }
        JsonNode codeNode = root.get(FIELD_CODE);
        int code = codeNode == null ? -1 : codeNode.asInt(-1);
        if (code != 0) {
            String message = root.path(FIELD_MESSAGE).asText("");
            return new Fetched(address, null, message.isBlank() ? "实例返回业务码 " + code : message);
        }
        JsonNode data = root.get(FIELD_DATA);
        if (data == null || data.isNull()) {
            return new Fetched(address, null, "实例无该任务的执行日志");
        }
        return new Fetched(address, data.asText(), null);
    }

    /**
     * 构建带回捞超时的客户端。
     *
     * @return RestClient
     */
    private RestClient client() {
        int configured = jobProperties.getExecutor().getHttpTimeoutMillis();
        int timeout = configured > 0 ? Math.min(configured, MAX_TIMEOUT_MILLIS) : MAX_TIMEOUT_MILLIS;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeout));
        factory.setReadTimeout(Duration.ofMillis(timeout));
        return restClientBuilderProvider.getIfAvailable(RestClient::builder)
                .clone()
                .requestFactory(factory)
                .requestInterceptor(InternalTokenSupport.interceptor())
                .build();
    }
}
