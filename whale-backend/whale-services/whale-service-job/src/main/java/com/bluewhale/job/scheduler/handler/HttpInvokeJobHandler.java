package com.bluewhale.job.scheduler.handler;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.job.scheduler.JobHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

/**
 * 远程 HTTP 调用执行器（Bean 名 {@code httpInvokeJobHandler}）。
 *
 * <p>将调度中心触发转发至业务服务的内网端点，用于承接业务服务迁移出来的
 * {@code @Scheduled} 任务：调度中心统一持有 cron / 重试 / 分片 / 执行日志，
 * 业务逻辑仍留在所属服务。</p>
 *
 * <p>{@code handler_param} 为 JSON：{@code {"service":"whale-crm","method":"POST",
 * "path":"/crm/internal/analytics/snapshot","body":{...}}}，其中 {@code body}
 * 可选（原样透传的 JSON 文本）；{@code method} 仅支持 GET / POST，缺省 POST。
 * 目标实例经 Nacos 服务发现解析（取首个实例），请求经
 * {@link InternalTokenSupport} 自动附加内网令牌头；目标端点一律为
 * {@code /xxx/internal/**}（网关侧已 internal-deny 屏蔽）。</p>
 *
 * <p>响应按 {@code Result} 结构解析：业务码非 0 视为失败并抛出
 * {@link BizException}，由调度引擎落 {@code job_log} 失败终态。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component("httpInvokeJobHandler")
public class HttpInvokeJobHandler implements JobHandler {

    /** 支持的 HTTP 方法：GET */
    private static final String METHOD_GET = "GET";

    /** 支持的 HTTP 方法：POST */
    private static final String METHOD_POST = "POST";

    /** Result 业务成功码 */
    private static final int SUCCESS_CODE = 0;

    /** 执行结果摘要长度上限 */
    private static final int MAX_SUMMARY_LENGTH = 500;

    /** 参数 JSON 解析器（不依赖容器 Bean，避免 Spring Boot 4 ObjectMapper 装配差异） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 服务发现客户端 */
    private final DiscoveryClient discoveryClient;

    /** 带内网令牌的出站客户端 */
    private final RestClient restClient;

    /**
     * 构造远程调用执行器
     *
     * @param discoveryClient 服务发现客户端（Nacos）
     * @param restClientBuilder RestClient 构建器（由 Spring Boot 自动配置提供）
     */
    public HttpInvokeJobHandler(DiscoveryClient discoveryClient, RestClient.Builder restClientBuilder) {
        this.discoveryClient = discoveryClient;
        this.restClient = restClientBuilder.requestInterceptor(InternalTokenSupport.interceptor()).build();
    }

    @Override
    public String execute(String param) throws Exception {
        JsonNode node = parseParam(param);
        String service = node.path("service").asText("");
        String method = node.path("method").asText(METHOD_POST).toUpperCase();
        String path = node.path("path").asText("");
        if (service.isBlank() || path.isBlank()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "job.invoke.param-missing",
                    "httpInvokeJobHandler 参数缺少 service 或 path：" + param, param);
        }
        if (!METHOD_GET.equals(method) && !METHOD_POST.equals(method)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "job.invoke.method-unsupported",
                    "httpInvokeJobHandler 仅支持 GET/POST，收到：" + method, method);
        }
        JsonNode bodyNode = node.get("body");
        String body = bodyNode == null || bodyNode.isNull() ? null : bodyNode.toString();
        URI uri = resolveBaseUri(service).resolve(path);
        String raw = invoke(uri, method, body);
        return summarize(service, method, path, raw);
    }

    /**
     * 解析执行器参数
     *
     * @param param 原始参数文本
     * @return 参数 JSON 节点
     * @throws Exception 参数非法 JSON 时抛出
     */
    private JsonNode parseParam(String param) throws Exception {
        if (param == null || param.isBlank()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "job.invoke.param-empty",
                    "httpInvokeJobHandler 参数为空，需 JSON：service/method/path/body");
        }
        return objectMapper.readTree(param);
    }

    /**
     * 经服务发现解析目标服务基地址
     *
     * @param service 目标服务名（Nacos 注册名）
     * @return 目标基地址
     */
    private URI resolveBaseUri(String service) {
        List<ServiceInstance> instances = discoveryClient.getInstances(service);
        if (instances == null || instances.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "job.invoke.service-unavailable",
                    "目标服务无可用实例：" + service, service);
        }
        return instances.get(0).getUri();
    }

    /**
     * 发起内网 HTTP 调用并返回原始响应体
     *
     * @param uri 目标地址
     * @param method HTTP 方法（GET / POST）
     * @param body 请求体 JSON 文本（可空）
     * @return 响应体原文
     */
    private String invoke(URI uri, String method, String body) {
        try {
            RestClient.RequestBodySpec spec = restClient.method(HttpMethod.valueOf(method)).uri(uri);
            if (body != null) {
                spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            return spec.retrieve().body(String.class);
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new BizException(ErrorCode.SYSTEM_ERROR, "job.invoke.remote-failed",
                    "远程任务调用失败 " + uri + "：" + reason, uri, reason);
        }
    }

    /**
     * 解析 {@code Result} 响应并生成执行结果摘要
     *
     * @param service 目标服务名
     * @param method HTTP 方法
     * @param path 目标路径
     * @param raw 响应体原文
     * @return 执行结果摘要
     */
    private String summarize(String service, String method, String path, String raw) {
        String codeText;
        String dataText;
        try {
            JsonNode root = objectMapper.readTree(raw == null ? "" : raw);
            codeText = root.path("code").asText("missing");
            JsonNode data = root.get("data");
            dataText = data == null || data.isNull() ? "" : data.toString();
            if (root.path("code").asInt(-1) != SUCCESS_CODE) {
                String message = root.path("message").asText("无错误信息");
                throw new BizException(ErrorCode.SYSTEM_ERROR, "job.invoke.remote-biz-failed",
                        "远程任务业务失败：" + message, message);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "job.invoke.response-parse-failed",
                    "远程任务响应解析失败：" + truncate(raw), truncate(raw));
        }
        String summary = "服务=" + service + " " + method + " " + path + " code=" + codeText
                + (dataText.isEmpty() ? "" : " data=" + dataText);
        log.info("httpInvokeJobHandler 执行完成：{}", summary);
        return truncate(summary);
    }

    /**
     * 按上限截断文本
     *
     * @param text 原始文本
     * @return 截断后的文本
     */
    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_SUMMARY_LENGTH ? text.substring(0, MAX_SUMMARY_LENGTH) : text;
    }
}
