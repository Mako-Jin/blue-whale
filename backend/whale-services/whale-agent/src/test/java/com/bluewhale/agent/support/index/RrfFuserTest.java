package com.bluewhale.agent.support.index;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RRF 融合测试：名次累加、去重、权重与截断。
 *
 * @author BlueWhale
 */
class RrfFuserTest {

    @Test
    @DisplayName("按 RRF 分数融合两路召回并去重")
    void shouldFuseByReciprocalRank() {
        List<SearchHit> bm25 = List.of(hit("a"), hit("b"), hit("c"));
        List<SearchHit> vector = List.of(hit("b"), hit("c"), hit("a"));
        List<SearchHit> fused = RrfFuser.fuse(List.of(
                new RrfFuser.Ranking(bm25, 1.0D),
                new RrfFuser.Ranking(vector, 1.0D)), RrfFuser.DEFAULT_K, 3);
        assertEquals(List.of("b", "a", "c"), ids(fused));
        assertEquals(3, fused.size(), "同一文档在多路命中应只保留一条");
        assertTrue(fused.get(0).getScore() > fused.get(1).getScore());
    }

    @Test
    @DisplayName("输出条数受 topK 限制")
    void shouldLimitTopK() {
        List<SearchHit> bm25 = List.of(hit("a"), hit("b"), hit("c"));
        List<SearchHit> fused = RrfFuser.fuse(List.of(new RrfFuser.Ranking(bm25, 1.0D)),
                RrfFuser.DEFAULT_K, 2);
        assertEquals(List.of("a", "b"), ids(fused));
    }

    @Test
    @DisplayName("权重为 0 的通道不参与融合")
    void shouldIgnoreZeroWeightRanking() {
        List<SearchHit> bm25 = List.of(hit("a"), hit("b"));
        List<SearchHit> vector = List.of(hit("b"), hit("c"));
        List<SearchHit> fused = RrfFuser.fuse(List.of(
                new RrfFuser.Ranking(bm25, 1.0D),
                new RrfFuser.Ranking(vector, 0D)), 60, 10);
        assertEquals(List.of("a", "b"), ids(fused));
        assertTrue(fused.stream().allMatch(item -> item.getScore() > 0D));
    }

    @Test
    @DisplayName("空输入返回空列表")
    void shouldReturnEmptyForEmptyInput() {
        assertTrue(RrfFuser.fuse(List.of(), 60, 5).isEmpty());
        assertTrue(RrfFuser.fuse(null, 60, 5).isEmpty());
        assertTrue(RrfFuser.fuse(List.of(new RrfFuser.Ranking(List.of(), 1.0D)), 60, 0).isEmpty());
    }

    /**
     * 构造命中对象。
     *
     * @param id 文档 ID
     * @return 命中对象
     */
    private static SearchHit hit(String id) {
        return SearchHit.builder().id(id).path("p/" + id).score(1.0F).build();
    }

    /**
     * 提取命中 ID 列表。
     *
     * @param hits 命中列表
     * @return ID 列表
     */
    private static List<String> ids(List<SearchHit> hits) {
        return hits.stream().map(SearchHit::getId).toList();
    }
}
