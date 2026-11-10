package com.bluewhale.assistant.support;

import com.bluewhale.assistant.config.AssistantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * whale-ai 工具注册表客户端：读取 {@code GET /v1/tools} 的工具编码全集。
 *
 * <p>用于 {@code asst_tool_grant} 授权落库前校验——工具白名单只能收敛、
 * 不能放大，未在注册表登记的工具编码一律拒绝（fail-closed）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistryClient {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 连接超时（秒） */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final AssistantProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    /**
     * 拉取 whale-ai 工具注册表条目（公开描述：名称、描述、风险级、权限码）。
     *
     * @return 工具描述列表（与 whale-ai {@code GET /v1/tools} 响应字段一致）
     * @throws IOException          网络失败或响应非 200
     * @throws InterruptedException 线程中断
     */
    public List<Map<String, Object>> listToolDescriptions() throws IOException, InterruptedException {
        String url = properties.getAi().getBaseUrl() + "/v1/tools";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getAi().getTimeoutSeconds()))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("whale-ai /v1/tools 返回 HTTP " + response.statusCode());
        }
        JsonNode tools = MAPPER.readTree(response.body()).path("tools");
        List<Map<String, Object>> result = new ArrayList<>();
        if (tools.isArray()) {
            for (JsonNode tool : tools) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", tool.path("name").asText(""));
                item.put("description", tool.path("description").asText(""));
                item.put("riskLevel", tool.path("riskLevel").asText(""));
                item.put("permission", tool.path("permission").asText(""));
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 拉取 whale-ai 工具注册表中的工具编码全集。
     *
     * @return 工具编码集合（如 knowledge_search/fact_get/doc_read/data_query/platform_op）
     * @throws IOException          网络失败或响应非 200
     * @throws InterruptedException 线程中断
     */
    public Set<String> listToolNames() throws IOException, InterruptedException {
        String url = properties.getAi().getBaseUrl() + "/v1/tools";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getAi().getTimeoutSeconds()))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("whale-ai /v1/tools 返回 HTTP " + response.statusCode());
        }
        JsonNode root = MAPPER.readTree(response.body());
        JsonNode tools = root.path("tools");
        Set<String> names = new HashSet<>();
        if (tools.isArray()) {
            for (JsonNode tool : tools) {
                String name = tool.path("name").asText("");
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        log.debug("whale-ai 工具注册表共 {} 个工具", names.size());
        return names;
    }
}
