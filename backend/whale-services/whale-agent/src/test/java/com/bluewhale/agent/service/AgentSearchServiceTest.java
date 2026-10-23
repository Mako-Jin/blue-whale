package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.support.index.AgentIndexService;
import com.bluewhale.agent.support.index.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 混合检索服务测试：RRF 融合与向量通道降级。
 *
 * @author BlueWhale
 */
class AgentSearchServiceTest {

    private static final String QUESTION = "网关路由";

    private AgentIndexService indexService;

    private EmbeddingService embeddingService;

    private AgentProperties properties;

    private AgentSearchService searchService;

    @BeforeEach
    void setUp() {
        indexService = mock(AgentIndexService.class);
        embeddingService = mock(EmbeddingService.class);
        properties = new AgentProperties();
        searchService = new AgentSearchService(indexService, embeddingService, properties);
        when(indexService.search(eq(QUESTION), anyInt(), any()))
                .thenReturn(List.of(hit("a"), hit("b"), hit("c")));
    }

    @Test
    @DisplayName("BM25 与向量双通道经 RRF 融合排序")
    void shouldFuseBothChannels() {
        when(embeddingService.available()).thenReturn(true);
        when(indexService.indexedDimension()).thenReturn(256);
        when(embeddingService.embed(QUESTION)).thenReturn(new float[256]);
        when(indexService.searchVector(any(), anyInt(), any()))
                .thenReturn(List.of(hit("b"), hit("c"), hit("a")));

        List<SearchHit> result = searchService.search(QUESTION, 3, Set.of());

        assertEquals(List.of("b", "a", "c"), ids(result));
    }

    @Test
    @DisplayName("关闭向量通道时返回纯 BM25 结果")
    void shouldReturnBm25WhenVectorDisabled() {
        properties.getSearch().setVectorEnabled(false);

        List<SearchHit> result = searchService.search(QUESTION, 2, Set.of());

        assertEquals(List.of("a", "b"), ids(result));
        verify(indexService, never()).searchVector(any(), anyInt(), any());
    }

    @Test
    @DisplayName("查询向量化失败时退回 BM25")
    void shouldFallbackWhenQueryVectorNull() {
        when(embeddingService.available()).thenReturn(true);
        when(indexService.indexedDimension()).thenReturn(256);
        when(embeddingService.embed(QUESTION)).thenReturn(null);

        List<SearchHit> result = searchService.search(QUESTION, 3, Set.of());

        assertEquals(List.of("a", "b", "c"), ids(result));
        verify(indexService, never()).searchVector(any(), anyInt(), any());
    }

    @Test
    @DisplayName("索引无向量时退回 BM25")
    void shouldFallbackWhenIndexHasNoVector() {
        when(embeddingService.available()).thenReturn(true);
        when(indexService.indexedDimension()).thenReturn(0);

        List<SearchHit> result = searchService.search(QUESTION, 3, Set.of());

        assertEquals(List.of("a", "b", "c"), ids(result));
        verify(indexService, never()).searchVector(any(), anyInt(), any());
    }

    @Test
    @DisplayName("向量检索异常时退回 BM25，不影响主链路")
    void shouldFallbackWhenVectorSearchThrows() {
        when(embeddingService.available()).thenReturn(true);
        when(indexService.indexedDimension()).thenReturn(256);
        when(embeddingService.embed(anyString())).thenReturn(new float[256]);
        when(indexService.searchVector(any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("索引不可用"));

        List<SearchHit> result = searchService.search(QUESTION, 3, Set.of());

        assertEquals(List.of("a", "b", "c"), ids(result));
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
