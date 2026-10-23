package com.bluewhale.agent.support.embedding;

import com.bluewhale.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容 embeddings 客户端：调用 {@code POST {base-url}/embeddings}。
 *
 * <p>API Key / 基地址未配置时回退 {@code whale.agent.llm} 同名配置；调用失败时返回
 * 与入参等长的 {@code null} 列表（该批文本不写向量），不阻断索引构建。日志不输出 Key。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 错误响应体截断长度 */
    private static final int ERROR_BODY_LIMIT = 300;

    private final AgentProperties properties;

    private final HttpClient httpClient;

    /**
     * 依据平台配置构建客户端。
     *
     * @param properties Agent 配置
     */
    public OpenAiEmbeddingProvider(AgentProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getEmbedding().getTimeoutMillis()))
                .build();
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public int dimension() {
        return Math.max(properties.getEmbedding().getDimension(), 0);
    }

    @Override
    public boolean available() {
        AgentProperties.Embedding embedding = properties.getEmbedding();
        return embedding.isEnabled() && StringUtils.hasText(baseUrl())
                && StringUtils.hasText(apiKey()) && StringUtils.hasText(embedding.getModel());
    }

    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        List<float[]> vectors = embedBatch(List.of(text));
        return vectors.isEmpty() ? null : vectors.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        if (!available() || texts.isEmpty()) {
            for (int i = 0; i < texts.size(); i++) {
                result.add(null);
            }
            return result;
        }
        int batchSize = Math.max(1, properties.getEmbedding().getBatchSize());
        for (int from = 0; from < texts.size(); from += batchSize) {
            int to = Math.min(from + batchSize, texts.size());
            List<float[]> parsed = request(texts.subList(from, to));
            result.addAll(parsed);
        }
        return result;
    }

    /**
     * 发送一批文本并解析向量。
     *
     * @param texts 本批文本
     * @return 与入参等长的向量列表；失败时全为 {@code null}
     */
    private List<float[]> request(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        try {
            HttpRequest request = buildRequest(texts);
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("embedding 调用失败：status={}, body={}", response.statusCode(), truncate(response.body()));
                return nullList(texts.size());
            }
            List<float[]> parsed = parseEmbeddings(response.body());
            if (parsed.size() != texts.size()) {
                log.warn("embedding 返回条数与入参不一致：expect={}, actual={}", texts.size(), parsed.size());
                return nullList(texts.size());
            }
            for (float[] vector : parsed) {
                vectors.add(vector);
            }
            return vectors;
        } catch (IOException e) {
            log.warn("embedding 调用异常：{}", e.getMessage());
            return nullList(texts.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("embedding 调用被中断");
            return nullList(texts.size());
        }
    }

    /**
     * 构造 embeddings 请求。
     *
     * @param texts 本批文本
     * @return HTTP 请求
     */
    private HttpRequest buildRequest(List<String> texts) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", properties.getEmbedding().getModel());
        ArrayNode input = body.putArray("input");
        for (String text : texts) {
            input.add(text);
        }
        return HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(baseUrl()) + "/embeddings"))
                .timeout(Duration.ofMillis(properties.getEmbedding().getTimeoutMillis()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    /**
     * 解析 {@code data[].embedding} 并逐条归一化。
     *
     * @param body 响应体
     * @return 向量列表；结构异常返回空列表
     */
    static List<float[]> parseEmbeddings(String body) {
        List<float[]> vectors = new ArrayList<>();
        try {
            JsonNode data = MAPPER.readTree(body).path("data");
            if (!data.isArray()) {
                return vectors;
            }
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                if (!embedding.isArray() || embedding.isEmpty()) {
                    continue;
                }
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                float[] normalized = EmbeddingProvider.normalize(vector);
                if (normalized != null) {
                    vectors.add(normalized);
                }
            }
            return vectors;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /**
     * 向量基地址：embedding 优先，回退 LLM 配置。
     *
     * @return 基地址
     */
    private String baseUrl() {
        String value = properties.getEmbedding().getBaseUrl();
        return StringUtils.hasText(value) ? value : properties.getLlm().getBaseUrl();
    }

    /**
     * API Key：embedding 优先，回退 LLM 配置。
     *
     * @return API Key
     */
    private String apiKey() {
        String value = properties.getEmbedding().getApiKey();
        return StringUtils.hasText(value) ? value : properties.getLlm().getApiKey();
    }

    /**
     * 生成全 {@code null} 列表。
     *
     * @param size 长度
     * @return 列表
     */
    private static List<float[]> nullList(int size) {
        List<float[]> vectors = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            vectors.add(null);
        }
        return vectors;
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
