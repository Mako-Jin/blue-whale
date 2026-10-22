package com.bluewhale.agent.support.llm;

import com.bluewhale.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * OpenAI 兼容客户端：使用 JDK {@link HttpClient} 调用 {@code /chat/completions}。
 *
 * <p>不引入额外 SDK；未配置 API Key 时 {@link #available()} 返回 {@code false}，
 * 由问答编排降级为检索摘要。日志与异常中不输出 Key。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class OpenAiClient implements LlmClient {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 流式响应前缀 */
    private static final String SSE_PREFIX = "data:";

    /** 流式结束标记 */
    private static final String SSE_DONE = "[DONE]";

    /** 错误响应体截断长度 */
    private static final int ERROR_BODY_LIMIT = 300;

    private final AgentProperties properties;

    private final HttpClient httpClient;

    /**
     * 依据平台配置构建客户端（连接超时取自 {@code bluewhale.agent.llm.timeout-millis}）。
     *
     * @param properties AGENT 服务配置
     */
    public OpenAiClient(AgentProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getLlm().getTimeoutMillis()))
                .build();
    }

    @Override
    public boolean available() {
        AgentProperties.Llm llm = properties.getLlm();
        return llm.isEnabled() && StringUtils.hasText(llm.getApiKey())
                && StringUtils.hasText(llm.getBaseUrl()) && StringUtils.hasText(llm.getModel());
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        if (!available()) {
            return null;
        }
        try {
            HttpRequest request = request(messages, false);
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("模型调用失败：status={}, body={}", response.statusCode(), truncate(response.body()));
                return null;
            }
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return content.isTextual() ? content.asText() : null;
        } catch (IOException e) {
            log.warn("模型调用异常：{}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("模型调用被中断");
            return null;
        }
    }

    @Override
    public LlmToolTurn completeWithTools(List<ChatMessage> messages, List<LlmToolSpec> tools) {
        if (!available()) {
            return null;
        }
        try {
            HttpRequest request = request(messages, false, tools);
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("模型工具调用失败：status={}, body={}", response.statusCode(), truncate(response.body()));
                return null;
            }
            return parseToolTurn(response.body());
        } catch (IOException e) {
            log.warn("模型工具调用异常：{}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("模型工具调用被中断");
            return null;
        }
    }

    /**
     * 解析非流式响应中的文本与工具调用。
     *
     * @param body 响应体
     * @return 单轮响应；结构异常返回 {@code null}
     */
    static LlmToolTurn parseToolTurn(String body) {
        try {
            JsonNode message = MAPPER.readTree(body).path("choices").path(0).path("message");
            JsonNode contentNode = message.path("content");
            String content = contentNode.isTextual() ? contentNode.asText() : null;
            List<LlmToolCall> calls = new ArrayList<>();
            JsonNode callNodes = message.path("tool_calls");
            if (callNodes.isArray()) {
                for (JsonNode call : callNodes) {
                    JsonNode function = call.path("function");
                    String id = call.path("id").asText();
                    String name = function.path("name").asText();
                    JsonNode arguments = function.path("arguments");
                    String raw = arguments.isTextual() ? arguments.asText() : arguments.toString();
                    if (StringUtils.hasText(name)) {
                        calls.add(new LlmToolCall(id, name, raw == null ? "{}" : raw));
                    }
                }
            }
            return new LlmToolTurn(content, calls);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void stream(List<ChatMessage> messages, Consumer<String> onDelta) throws IOException {
        if (!available()) {
            throw new IOException("模型未配置");
        }
        HttpRequest request = request(messages, true, List.of());
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("模型调用被中断", e);
        }
        try (InputStream body = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            if (response.statusCode() != 200) {
                log.warn("模型流式调用失败：status={}", response.statusCode());
                throw new IOException("模型返回状态 " + response.statusCode());
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(SSE_PREFIX)) {
                    continue;
                }
                String payload = line.substring(SSE_PREFIX.length()).trim();
                if (payload.isEmpty() || SSE_DONE.equals(payload)) {
                    continue;
                }
                String delta = deltaOf(payload);
                if (delta != null && !delta.isEmpty()) {
                    onDelta.accept(delta);
                }
            }
        }
    }

    /**
     * 解析流式增量。
     *
     * @param payload 单行 JSON
     * @return 增量文本；无内容返回 {@code null}
     */
    private static String deltaOf(String payload) {
        try {
            JsonNode root = MAPPER.readTree(payload);
            JsonNode content = root.path("choices").path(0).path("delta").path("content");
            return content.isTextual() ? content.asText() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 构造请求。
     *
     * @param messages 消息列表
     * @param stream   是否流式
     * @return HTTP 请求
     */
    private HttpRequest request(List<ChatMessage> messages, boolean stream) {
        return request(messages, stream, List.of());
    }

    /**
     * 构造请求（可携带工具定义）。
     *
     * @param messages 消息列表
     * @param stream   是否流式
     * @param tools    工具定义（空表示普通对话）
     * @return HTTP 请求
     */
    private HttpRequest request(List<ChatMessage> messages, boolean stream, List<LlmToolSpec> tools) {
        AgentProperties.Llm llm = properties.getLlm();
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", llm.getModel());
        body.put("stream", stream);
        body.put("temperature", llm.getTemperature());
        body.put("max_tokens", llm.getMaxTokens());
        ArrayNode array = body.putArray("messages");
        for (ChatMessage message : messages) {
            array.add(messageNode(message));
        }
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolArray = body.putArray("tools");
            for (LlmToolSpec tool : tools) {
                ObjectNode item = toolArray.addObject();
                item.put("type", "function");
                ObjectNode function = item.putObject("function");
                function.put("name", tool.name());
                function.put("description", tool.description());
                function.set("parameters", tool.parameters());
            }
            body.put("tool_choice", "auto");
        }
        return HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(llm.getBaseUrl()) + "/chat/completions"))
                .timeout(Duration.ofMillis(llm.getTimeoutMillis()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + llm.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    /**
     * 消息序列化（兼容 tool 角色与 assistant 工具调用）。
     *
     * @param message 消息
     * @return JSON 节点
     */
    private static ObjectNode messageNode(ChatMessage message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("role", message.role());
        if (message.content() == null) {
            node.putNull("content");
        } else {
            node.put("content", message.content());
        }
        if (StringUtils.hasText(message.toolCallId())) {
            node.put("tool_call_id", message.toolCallId());
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            ArrayNode calls = node.putArray("tool_calls");
            for (LlmToolCall call : message.toolCalls()) {
                ObjectNode item = calls.addObject();
                item.put("id", call.id());
                item.put("type", "function");
                ObjectNode function = item.putObject("function");
                function.put("name", call.name());
                function.put("arguments", call.arguments());
            }
        }
        return node;
    }

    /**
     * 去除基地址末尾斜杠。
     *
     * @param url 基地址
     * @return 归一化地址
     */
    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 截断错误响应体。
     *
     * @param body 响应体
     * @return 截断结果
     */
    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= ERROR_BODY_LIMIT ? body : body.substring(0, ERROR_BODY_LIMIT) + "...";
    }
}
