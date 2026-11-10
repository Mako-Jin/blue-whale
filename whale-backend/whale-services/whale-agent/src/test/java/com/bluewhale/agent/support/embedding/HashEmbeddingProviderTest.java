package com.bluewhale.agent.support.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地哈希向量测试：维度、归一化、确定性与相似度排序。
 *
 * @author BlueWhale
 */
class HashEmbeddingProviderTest {

    private final HashEmbeddingProvider provider = new HashEmbeddingProvider(0);

    @Test
    @DisplayName("默认维度 256，输出单位向量")
    void shouldProduceUnitVectorWithDefaultDimension() {
        assertEquals(HashEmbeddingProvider.DEFAULT_DIMENSION, provider.dimension());
        float[] vector = provider.embed("网关路由配置");
        assertNotNull(vector);
        assertEquals(256, vector.length);
        double norm = 0D;
        for (float value : vector) {
            norm += (double) value * value;
        }
        assertEquals(1D, norm, 1e-5);
    }

    @Test
    @DisplayName("同一文本多次向量化结果一致")
    void shouldBeDeterministic() {
        assertArrayEquals(provider.embed("whale-gateway 路由表"), provider.embed("whale-gateway 路由表"));
    }

    @Test
    @DisplayName("空文本返回 null")
    void shouldReturnNullForBlankText() {
        assertNull(provider.embed(""));
        assertNull(provider.embed("   "));
        assertNull(provider.embed(null));
    }

    @Test
    @DisplayName("相关文本余弦相似度高于无关文本")
    void shouldRankRelatedTextHigher() {
        float[] query = provider.embed("网关路由");
        float[] related = provider.embed("网关路由配置说明");
        float[] unrelated = provider.embed("用户登录密码重置");
        assertTrue(cosine(query, related) > cosine(query, unrelated),
                "相关文本相似度应更高：related=" + cosine(query, related)
                        + "，unrelated=" + cosine(query, unrelated));
    }

    @Test
    @DisplayName("特征提取包含英文词、中文单字与二元组")
    void shouldExtractAsciiHanAndBigramFeatures() {
        List<String> features = HashEmbeddingProvider.features("gateway 网关");
        assertTrue(features.contains("w:gateway"));
        assertTrue(features.contains("c:网"));
        assertTrue(features.contains("b:网关"));
    }

    /**
     * 计算余弦相似度。
     *
     * @param left  向量
     * @param right 向量
     * @return 相似度
     */
    private static double cosine(float[] left, float[] right) {
        double dot = 0D;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
        }
        return dot;
    }
}
