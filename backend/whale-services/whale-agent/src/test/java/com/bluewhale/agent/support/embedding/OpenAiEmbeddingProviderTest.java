package com.bluewhale.agent.support.embedding;

import com.bluewhale.agent.config.AgentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI 兼容 embedding 客户端测试：响应解析、归一化与异常降级。
 *
 * @author BlueWhale
 */
class OpenAiEmbeddingProviderTest {

    @Test
    @DisplayName("解析 data[].embedding 并做 L2 归一化")
    void shouldParseAndNormalizeEmbeddings() {
        List<float[]> vectors = OpenAiEmbeddingProvider.parseEmbeddings(
                "{\"data\":[{\"embedding\":[3,4]},{\"embedding\":[0,5]}]}");
        assertEquals(2, vectors.size());
        assertEquals(0.6F, vectors.get(0)[0], 1e-6);
        assertEquals(0.8F, vectors.get(0)[1], 1e-6);
        assertEquals(1.0F, vectors.get(1)[1], 1e-6);
    }

    @Test
    @DisplayName("非 JSON 或缺少 data 时返回空列表")
    void shouldReturnEmptyOnMalformedBody() {
        assertTrue(OpenAiEmbeddingProvider.parseEmbeddings("not-json").isEmpty());
        assertTrue(OpenAiEmbeddingProvider.parseEmbeddings("{\"data\":[]}").isEmpty());
        assertTrue(OpenAiEmbeddingProvider.parseEmbeddings("{}").isEmpty());
    }

    @Test
    @DisplayName("未配置 Key 时不可用，批量向量化返回全 null")
    void shouldBeUnavailableWithoutApiKey() {
        AgentProperties properties = new AgentProperties();
        properties.getEmbedding().setProvider("openai");
        OpenAiEmbeddingProvider provider = new OpenAiEmbeddingProvider(properties);
        assertFalse(provider.available());
        List<float[]> vectors = provider.embedBatch(List.of("网关", "路由"));
        assertEquals(2, vectors.size());
        assertTrue(vectors.stream().allMatch(item -> item == null));
    }
}
