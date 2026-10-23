package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.support.embedding.EmbeddingProvider;
import com.bluewhale.agent.support.embedding.HashEmbeddingProvider;
import com.bluewhale.agent.support.embedding.OpenAiEmbeddingProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 向量化服务：按配置选择 embedding 提供方并对上层屏蔽差异。
 *
 * <p>选择规则（{@code whale.agent.embedding.provider}）：</p>
 * <ul>
 *     <li>{@code hash}：本地确定性哈希向量（默认 256 维），无出网；</li>
 *     <li>{@code openai}：OpenAI 兼容 {@code /embeddings}，未配置 Key 时降级为哈希；</li>
 *     <li>{@code auto}：有 Key 走远程，否则走哈希；</li>
 *     <li>{@code none} 或 {@code enabled=false}：关闭向量通道。</li>
 * </ul>
 *
 * <p>{@link #signature()} 记录「提供方 + 维度」，索引侧据此判断是否需要全量重建。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class EmbeddingService {

    /** 关闭向量通道时的签名 */
    private static final String SIGNATURE_NONE = "none";

    private final AgentProperties properties;

    private volatile EmbeddingProvider provider;

    private volatile OpenAiEmbeddingProvider openAiProvider;

    /**
     * 构造向量化服务。
     *
     * @param properties Agent 配置
     */
    public EmbeddingService(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 启动时解析提供方（测试可直接调用 {@link #resolve()}）。
     */
    @PostConstruct
    public void init() {
        resolve();
    }

    /**
     * 依据配置解析提供方；未知模式回落本地哈希。
     */
    public void resolve() {
        AgentProperties.Embedding embedding = properties.getEmbedding();
        if (!embedding.isEnabled()) {
            provider = null;
            log.info("向量通道已关闭（whale.agent.embedding.enabled=false）");
            return;
        }
        String mode = StringUtils.hasText(embedding.getProvider())
                ? embedding.getProvider().trim().toLowerCase(Locale.ROOT) : "auto";
        openAiProvider = new OpenAiEmbeddingProvider(properties);
        if ("none".equals(mode)) {
            provider = null;
            log.info("向量通道已关闭（provider=none）");
            return;
        }
        if ("openai".equals(mode) || ("auto".equals(mode) && openAiProvider.available())) {
            if (openAiProvider.available()) {
                provider = openAiProvider;
                log.info("向量提供方：openai（model={}，dimension={}）", embedding.getModel(), openAiProvider.dimension());
                return;
            }
            log.warn("embedding provider=openai 但未配置 api-key，降级为本地哈希向量");
        }
        provider = new HashEmbeddingProvider(embedding.getDimension());
        log.info("向量提供方：hash（dimension={}）", provider.dimension());
    }

    /**
     * 当前提供方是否可用。
     *
     * @return 可用返回 {@code true}
     */
    public boolean available() {
        EmbeddingProvider current = provider;
        return current != null && current.available();
    }

    /**
     * 当前提供方名称。
     *
     * @return hash / openai；关闭时为 none
     */
    public String providerName() {
        EmbeddingProvider current = provider;
        return current == null ? SIGNATURE_NONE : current.name();
    }

    /**
     * 当前提供方维度（未知返回 0）。
     *
     * @return 维度
     */
    public int dimension() {
        EmbeddingProvider current = provider;
        return current == null ? 0 : current.dimension();
    }

    /**
     * 索引签名：提供方 + 维度，用于检测换模型/换维度后需全量重建。
     *
     * @return 签名（如 {@code hash:256}、{@code openai:0}、{@code none}）
     */
    public String signature() {
        EmbeddingProvider current = provider;
        if (current == null || !current.available()) {
            return SIGNATURE_NONE;
        }
        return current.name() + ":" + current.dimension();
    }

    /**
     * 单文本向量化（异常降级为 {@code null}）。
     *
     * @param text 文本
     * @return 向量；不可用或失败返回 {@code null}
     */
    public float[] embed(String text) {
        EmbeddingProvider current = provider;
        if (current == null || !StringUtils.hasText(text)) {
            return null;
        }
        try {
            return current.embed(text);
        } catch (RuntimeException e) {
            log.warn("文本向量化失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 批量向量化（异常降级为全 {@code null}）。
     *
     * @param texts 文本列表
     * @return 与入参等长的向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingProvider current = provider;
        if (current == null || texts == null || texts.isEmpty()) {
            return nullVectors(texts == null ? 0 : texts.size());
        }
        try {
            return current.embedBatch(texts);
        } catch (RuntimeException e) {
            log.warn("批量向量化失败：{}", e.getMessage());
            return nullVectors(texts.size());
        }
    }

    /**
     * 生成全 {@code null} 向量列表。
     *
     * @param size 长度
     * @return 列表
     */
    private static List<float[]> nullVectors(int size) {
        List<float[]> vectors = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            vectors.add(null);
        }
        return vectors;
    }
}
