package com.bluewhale.agent.support.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF（Reciprocal Rank Fusion）融合器：按「通道权重 / (k + 名次)」累加多路召回分数。
 *
 * <p>只依赖名次而非原始分数，天然消除 BM25 与余弦相似度量纲差异；同一文档在多个
 * 通道命中时累加，未命中通道不加分。输出按融合分降序、同分按文档 ID 升序，保证稳定。</p>
 *
 * @author BlueWhale
 */
public final class RrfFuser {

    /** 默认平滑常数（RRF 论文取值） */
    public static final int DEFAULT_K = 60;

    /** 工具类不允许实例化 */
    private RrfFuser() {
    }

    /**
     * 单路召回结果。
     *
     * @param hits   按相关度降序的命中（名次从 1 起）
     * @param weight 通道权重（小于等于 0 时该通道不参与）
     */
    public record Ranking(List<SearchHit> hits, double weight) {
    }

    /**
     * 融合多路召回。
     *
     * @param rankings 召回路列表
     * @param k        平滑常数（小于等于 0 时回落 {@link #DEFAULT_K}）
     * @param topK     输出条数
     * @return 融合后的命中（带融合分数）
     */
    public static List<SearchHit> fuse(List<Ranking> rankings, int k, int topK) {
        if (rankings == null || rankings.isEmpty() || topK <= 0) {
            return List.of();
        }
        int smooth = k > 0 ? k : DEFAULT_K;
        Map<String, Accumulator> merged = new HashMap<>();
        for (Ranking ranking : rankings) {
            if (ranking == null || ranking.hits() == null || ranking.weight() <= 0D) {
                continue;
            }
            int rank = 0;
            for (SearchHit hit : ranking.hits()) {
                if (hit == null || hit.getId() == null) {
                    continue;
                }
                rank++;
                Accumulator accumulator = merged.computeIfAbsent(hit.getId(), id -> new Accumulator(hit));
                accumulator.score += ranking.weight() / (smooth + rank);
            }
        }
        List<Accumulator> ordered = new ArrayList<>(merged.values());
        ordered.sort(Comparator.comparingDouble((Accumulator item) -> item.score).reversed()
                .thenComparing(item -> item.hit.getId()));
        List<SearchHit> result = new ArrayList<>(Math.min(topK, ordered.size()));
        for (Accumulator item : ordered) {
            if (result.size() >= topK) {
                break;
            }
            result.add(item.hit.withScore(item.score));
        }
        return result;
    }

    /**
     * 融合累加器：保留首次出现的命中对象与累计分数。
     */
    private static final class Accumulator {

        /** 命中对象 */
        private final SearchHit hit;

        /** 累计 RRF 分数 */
        private double score;

        /**
         * 构造累加器。
         *
         * @param hit 命中对象
         */
        private Accumulator(SearchHit hit) {
            this.hit = hit;
        }
    }
}
