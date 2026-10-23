package com.bluewhale.agent.support.index;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.service.AgentIndexMetaService;
import com.bluewhale.agent.service.EmbeddingService;
import com.bluewhale.agent.service.FactStoreService;
import com.bluewhale.agent.service.KnowledgeIngestService;
import com.bluewhale.agent.support.ingest.ChunkerRegistry;
import com.bluewhale.agent.support.ingest.DocumentChunker;
import com.bluewhale.agent.support.ingest.RepositoryScanner;
import com.bluewhale.agent.support.ingest.chunker.ConfigChunker;
import com.bluewhale.agent.support.ingest.chunker.JavaChunker;
import com.bluewhale.agent.support.ingest.chunker.MarkdownChunker;
import com.bluewhale.agent.support.ingest.chunker.SqlChunker;
import com.bluewhale.agent.support.ingest.chunker.TypeScriptChunker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 索引冒烟测试：用本仓真实数据建索引，验证「网关路由」类问题能命中网关配置与架构文档。
 *
 * <p>名称以 {@code IT} 结尾，默认不进入 {@code mvn test}，需显式
 * {@code mvn -Dtest=AgentIndexIT test} 执行（依赖整仓文件）。</p>
 *
 * @author BlueWhale
 */
class AgentIndexIT {

    private Path indexDir;

    private AgentIndexService indexService;

    private KnowledgeIngestService ingestService;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() throws Exception {
        AgentProperties properties = new AgentProperties();
        indexDir = Path.of("target", "agent-index-it");
        deleteRecursively(indexDir);
        properties.setIndexDir(indexDir.toString());
        properties.getIngest().setBuildOnStartup(false);
        properties.getIngest().setScanIntervalSeconds(0);
        properties.getEmbedding().setProvider("hash");
        List<DocumentChunker> chunkers = List.of(new MarkdownChunker(), new JavaChunker(),
                new TypeScriptChunker(), new SqlChunker(), new ConfigChunker());
        ChunkerRegistry registry = new ChunkerRegistry(chunkers);
        RepositoryScanner scanner = new RepositoryScanner(properties, registry);
        embeddingService = new EmbeddingService(properties);
        embeddingService.init();
        indexService = new AgentIndexService(properties, embeddingService);
        indexService.init();
        ingestService = new KnowledgeIngestService(properties, scanner, registry, indexService,
                mock(AgentIndexMetaService.class), mock(FactStoreService.class), embeddingService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ingestService.shutdown();
        indexService.destroy();
    }

    @Test
    @DisplayName("真实仓库建索引后，网关路由查询命中配置或架构文档")
    void shouldIndexRepositoryAndFindGatewayRoute() {
        ingestService.rebuild();
        assertTrue(indexService.count() > 0, "索引块数应大于 0");
        List<SearchHit> hits = indexService.search("网关路由", 3, Set.of());
        assertFalse(hits.isEmpty(), "应命中知识块");
        boolean matched = hits.stream().anyMatch(hit -> hit.getPath().contains("gateway")
                || hit.getPath().contains("service-catalog"));
        assertTrue(matched, "前 3 命中应包含网关配置或 service-catalog，实际="
                + hits.stream().map(SearchHit::getPath).toList());
    }

    @Test
    @DisplayName("真实仓库建索引后写入向量，向量检索可命中且维度一致")
    void shouldIndexVectorsAndSearchByVector() {
        ingestService.rebuild();
        assertEquals(256, indexService.indexedDimension(), "哈希向量维度应为 256");
        assertTrue(indexService.indexedSignature().startsWith("hash:"), "签名应记录哈希提供方");
        float[] query = embeddingService.embed("网关路由");
        List<SearchHit> hits = indexService.searchVector(query, 5, Set.of());
        assertFalse(hits.isEmpty(), "向量检索应命中知识块");
        assertTrue(hits.stream().allMatch(hit -> hit.getScore() >= -1.0F && hit.getScore() <= 1.0F),
                "余弦相似度应在 [-1, 1] 区间");
    }

    /**
     * 递归删除目录。
     *
     * @param path 目录
     * @throws Exception 删除失败
     */
    private void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path file : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }
}
