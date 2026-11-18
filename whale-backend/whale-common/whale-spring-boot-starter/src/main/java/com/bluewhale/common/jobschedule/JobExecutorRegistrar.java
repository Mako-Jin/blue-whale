package com.bluewhale.common.jobschedule;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 执行器注册器：把本实例的地址与「支持的任务编码」上报给调度中心，并周期心跳。
 *
 * <p>这是对齐 xxl-job executor 的那一层：调度中心不再依赖写死的地址，而是通过注册表知道
 * 「谁在线、谁有这个任务」。上报的任务编码直接取自 {@link RemoteJobRegistry}，
 * 因此灰度期不同版本的实例只会被分配到各自真实支持的任务。</p>
 *
 * <p>失败不影响业务服务可用性：注册失败只告警，心跳会持续重试；心跳返回实例不存在时自动补注册。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class JobExecutorRegistrar implements ApplicationListener<ApplicationReadyEvent>, ApplicationContextAware,
        EnvironmentAware, DisposableBean {

    /** 注册接口路径 */
    private static final String REGISTER_PATH = "/job/internal/executor/register";

    /** 心跳接口路径 */
    private static final String BEAT_PATH = "/job/internal/executor/beat";

    /** 首次心跳延迟：留出调度中心接收注册的时间 */
    private static final long INITIAL_DELAY_MS = 5000L;

    /** 心跳线程名 */
    private static final String HEARTBEAT_THREAD_NAME = "whale-job-executor-beat";

    /** 兜底服务端口 */
    private static final int DEFAULT_SERVER_PORT = 8080;

    /** 毫秒换算基数 */
    private static final long MILLIS_PER_SECOND = 1000L;

    private final JobScheduleProperties properties;

    private final RemoteJobRegistry registry;

    private final ObjectMapper objectMapper;

    private final RestClient client;

    private ApplicationContext applicationContext;

    private Environment environment;

    private ScheduledExecutorService heartbeatExecutor;

    private String appName;

    private String address;

    /**
     * 构造注册器。
     *
     * @param properties   配置项
     * @param registry     任务注册表
     * @param objectMapper JSON 解析器（用于判定心跳响应）
     */
    public JobExecutorRegistrar(JobScheduleProperties properties, RemoteJobRegistry registry,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.client = buildClient(properties.getExecutor().getTimeoutMillis());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        this.appName = resolveAppName();
        this.address = resolveAddress();
        register();
        startHeartbeat();
    }

    @Override
    public void destroy() {
        deregister();
        awaitRunningJobs();
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
    }

    /**
     * 停机等待在途任务结束：注销只停止新派发，已派发到本实例的任务仍需跑完，
     * 否则执行流水会永远停在「执行中」。等待有上限，避免卡住整个进程关闭。
     */
    private void awaitRunningJobs() {
        long timeoutMillis = Math.max(properties.getExecutor().getShutdownAwaitSeconds(), 0) * MILLIS_PER_SECOND;
        if (timeoutMillis <= 0 || registry.runningCount() <= 0) {
            return;
        }
        log.info("执行器停机等待在途任务: app={}, address={}, running={}",
                appName, address, registry.runningCount());
        if (!registry.awaitIdle(timeoutMillis)) {
            log.warn("执行器停机等待超时，仍有在途任务未结束: app={}, address={}, running={}",
                    appName, address, registry.runningCount());
        }
    }

    /**
     * 上报注册。
     */
    private void register() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", properties.getExecutor().getTenantId());
        body.put("appName", appName);
        body.put("address", address);
        body.put("jobCodes", new ArrayList<>(registry.codes()));
        try {
            client.post()
                    .uri(baseUrl() + REGISTER_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebHeaders.TENANT_ID, String.valueOf(properties.getExecutor().getTenantId()))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("执行器注册成功: server={}, app={}, address={}, codes={}",
                    baseUrl(), appName, address, registry.codes());
        } catch (RuntimeException e) {
            log.warn("执行器注册失败（心跳将自动重试）: server={}, app={}, address={}, reason={}",
                    baseUrl(), appName, address, e.getMessage());
        }
    }

    /**
     * 心跳续约；返回实例不存在时补注册。
     */
    private void beat() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", properties.getExecutor().getTenantId());
        body.put("appName", appName);
        body.put("address", address);
        try {
            String response = client.put()
                    .uri(baseUrl() + BEAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebHeaders.TENANT_ID, String.valueOf(properties.getExecutor().getTenantId()))
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (!succeeded(response)) {
                log.warn("执行器心跳返回实例不存在，重新注册: app={}, address={}", appName, address);
                register();
            }
        } catch (RuntimeException e) {
            log.warn("执行器心跳失败: app={}, address={}, reason={}", appName, address, e.getMessage());
        }
    }

    /**
     * 停机注销，避免调度中心继续向已退出实例派发任务。
     */
    private void deregister() {
        if (!StringUtils.hasText(appName) || !StringUtils.hasText(address)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", properties.getExecutor().getTenantId());
        body.put("appName", appName);
        body.put("address", address);
        try {
            client.method(HttpMethod.DELETE)
                    .uri(baseUrl() + REGISTER_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebHeaders.TENANT_ID, String.valueOf(properties.getExecutor().getTenantId()))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("执行器已注销: app={}, address={}", appName, address);
        } catch (RuntimeException e) {
            log.warn("执行器注销失败（心跳超时后自动离线）: app={}, address={}, reason={}",
                    appName, address, e.getMessage());
        }
    }

    /**
     * 启动心跳线程。
     */
    private void startHeartbeat() {
        long interval = Math.max(properties.getExecutor().getHeartbeatIntervalMs(), 1000L);
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, HEARTBEAT_THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        heartbeatExecutor.scheduleWithFixedDelay(this::beat, INITIAL_DELAY_MS, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * 判定统一响应是否成功且数据为真。
     *
     * @param response 响应体
     * @return 心跳成功返回 {@code true}
     */
    private boolean succeeded(String response) {
        if (!StringUtils.hasText(response)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("code").asInt(-1) == 0 && root.path("data").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析执行器应用名。
     *
     * @return 应用名
     */
    private String resolveAppName() {
        if (StringUtils.hasText(properties.getExecutor().getAppName())) {
            return properties.getExecutor().getAppName();
        }
        String name = environment == null ? null : environment.getProperty("spring.application.name");
        return StringUtils.hasText(name) ? name : "unknown";
    }

    /**
     * 解析本实例对外地址。
     *
     * @return {@code host:port}
     */
    private String resolveAddress() {
        if (StringUtils.hasText(properties.getExecutor().getAddress())) {
            return properties.getExecutor().getAddress();
        }
        return resolveHost() + ":" + resolvePort();
    }

    /**
     * 解析实例 IP：优先 Nacos 显式配置，其次本机地址（容器内即容器 IP）。
     *
     * @return IP
     */
    private String resolveHost() {
        String nacosIp = environment == null ? null : environment.getProperty("spring.cloud.nacos.discovery.ip");
        if (StringUtils.hasText(nacosIp)) {
            return nacosIp;
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("无法解析本机地址，回退 127.0.0.1", e);
            return "127.0.0.1";
        }
    }

    /**
     * 解析服务端口：优先取已就绪的实际端口（支持随机端口），其次配置端口。
     *
     * @return 端口
     */
    private int resolvePort() {
        if (environment == null) {
            return DEFAULT_SERVER_PORT;
        }
        Integer actual = environment.getProperty("local.server.port", Integer.class);
        if (actual != null && actual > 0) {
            return actual;
        }
        return environment.getProperty("server.port", Integer.class, DEFAULT_SERVER_PORT);
    }

    /**
     * 调度中心地址（去尾部斜杠）。
     *
     * @return 基础地址
     */
    private String baseUrl() {
        String url = properties.getExecutor().getServerUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 构造带内网令牌与超时的 RestClient。
     *
     * @param timeoutMillis 超时毫秒数
     * @return RestClient
     */
    private static RestClient buildClient(int timeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMillis));
        factory.setReadTimeout(Duration.ofMillis(timeoutMillis));
        return InternalTokenSupport.builder().requestFactory(factory).build();
    }
}
