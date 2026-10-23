package com.bluewhale.agent.support.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本向量提供方：把文本转换为定长浮点向量，供 Lucene KNN 检索使用。
 *
 * <p>实现需保证同一文本在多次调用与多次进程启动间输出一致，否则索引向量与查询向量不可比。</p>
 *
 * @author BlueWhale
 */
public interface EmbeddingProvider {

    /**
     * 提供方名称（写入索引签名，用于维度/模型变更时触发全量重建）。
     *
     * @return hash / openai
     */
    String name();

    /**
     * 向量维度；未知（远程模型且未配置维度）返回 0。
     *
     * @return 维度
     */
    int dimension();

    /**
     * 提供方是否可用（本地哈希恒为 {@code true}，远程模型要求配置齐全）。
     *
     * @return 可用返回 {@code true}
     */
    boolean available();

    /**
     * 单文本向量化。
     *
     * @param text 文本
     * @return 归一化向量；空文本或失败返回 {@code null}
     */
    float[] embed(String text);

    /**
     * 批量向量化（默认逐个调用 {@link #embed(String)}）。
     *
     * @param texts 文本列表
     * @return 与入参等长的向量列表，元素可能为 {@code null}
     */
    default List<float[]> embedBatch(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }

    /**
     * L2 归一化工具（各提供方统一输出单位向量，便于余弦相似度比较）。
     *
     * @param vector 原始向量
     * @return 归一化向量；全零或空返回 {@code null}
     */
    static float[] normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }
        double sum = 0D;
        for (float value : vector) {
            sum += (double) value * value;
        }
        if (sum <= 0D) {
            return null;
        }
        float scale = (float) (1D / Math.sqrt(sum));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }
}
