package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.support.index.AgentIndexService;
import com.bluewhale.agent.support.index.RrfFuser;
import com.bluewhale.agent.support.index.SearchHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索服务：BM25 + 向量双通道召回，经 RRF 融合后返回统一排序。
 *
 * <p>向量通道不可用（未启用、索引无向量、查询向量化失败）时自动回落纯 BM25，
 * 保证任何配置下检索链路可用；控制器、问答编排与 MCP 工具共用此入口。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSearchService {

    private final AgentIndexService indexService;

    private final EmbeddingService embeddingService;

    private final AgentProperties properties;

    /**
     * 执行检索。
     *
     * @param question 用户问题
     * @param topK 期望条数（为空取默认值，超过上限按上限截断）
     * @param kinds 知识类型过滤（空表示不过滤）
     * @return 命中结果（融合分数）
     */
    public List<SearchHit> search(String question, Integer topK, Set<String> kinds) {
        int max = properties.getSearch().getMaxTopK();
        int effective = topK == null ? properties.getSearch().getDefaultTopK() : Math.min(topK, max);
        if (effective <= 0) {
            effective = properties.getSearch().getDefaultTopK();
        }
        int candidate = Math.max(effective, properties.getSearch().getCandidateTopK());
        List<SearchHit> bm25 = indexService.search(question, candidate, kinds);
        List<SearchHit> vector = vectorHits(question, candidate, kinds);
        if (vector.isEmpty()) {
            return limit(bm25, effective);
        }
        return RrfFuser.fuse(List.of(
                        new RrfFuser.Ranking(bm25, properties.getSearch().getBm25Weight()),
                        new RrfFuser.Ranking(vector, properties.getSearch().getVectorWeight())),
                properties.getSearch().getRrfK(), effective);
    }

    /**
     * 向量通道召回（异常/不可用时返回空列表）。
     *
     * @param question 用户问题
     * @param topK 候选条数
     * @param kinds 知识类型过滤
     * @return 向量命中（按相似度降序）
     */
    private List<SearchHit> vectorHits(String question, int topK, Set<String> kinds) {
        if (!properties.getSearch().isVectorEnabled() || !embeddingService.available()
                || indexService.indexedDimension() <= 0) {
            return List.of();
        }
        float[] queryVector = embeddingService.embed(question);
        if (queryVector == null || queryVector.length != indexService.indexedDimension()) {
            return List.of();
        }
        try {
            return indexService.searchVector(queryVector, topK, kinds);
        } catch (RuntimeException e) {
            log.warn("向量检索失败，本次退回纯 BM25：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 截断结果列表。
     *
     * @param hits 命中列表
     * @param topK 条数
     * @return 截断结果
     */
    private static List<SearchHit> limit(List<SearchHit> hits, int topK) {
        return hits.size() <= topK ? hits : hits.subList(0, topK);
    }

    /**
     * 解析逗号分隔的知识类型。
     *
     * @param csv 逗号分隔串（如 {@code code,doc}）
     * @return 类型集合；入参为空返回空集合
     */
    public static Set<String> parseKinds(String csv) {
        Set<String> kinds = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return kinds;
        }
        for (String part : csv.split(",")) {
            String kind = part.trim();
            if (!kind.isEmpty()) {
                kinds.add(kind);
            }
        }
        return kinds;
    }
}
