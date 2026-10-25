package com.bluewhale.agent.support.index;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.service.EmbeddingService;
import com.bluewhale.agent.support.embedding.HashEmbeddingProvider;
import com.bluewhale.agent.support.ingest.Chunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 索引向量测试：写入 KNN 向量、维度元数据与向量检索排序（本地哈希向量，不依赖外部服务）。
 *
 * @author BlueWhale
 */
class AgentIndexVectorTest {

    @TempDir
    Path indexDir;

    private AgentIndexService indexService;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() throws Exception {
        AgentProperties properties = new AgentProperties();
        properties.setIndexDir(indexDir.toString());
        properties.getEmbedding().setProvider("hash");
        embeddingService = new EmbeddingService(properties);
        embeddingService.init();
        indexService = new AgentIndexService(properties, embeddingService);
        indexService.init();
        indexService.addFileChunks("deploy/gateway-routes.yml", "hash-1", List.of(
                chunk("config", "deploy/gateway-routes.yml", "网关路由表",
                        "spring cloud gateway routes predicate path 网关 路由 转发 配置")));
        indexService.addFileChunks("backend/auth/LoginService.java", "hash-2", List.of(
                chunk("code", "backend/auth/LoginService.java", "LoginService",
                        "用户登录认证 token 刷新 jwt 密码校验")));
        indexService.addFileChunks("docs/workflow.md", "hash-3", List.of(
                chunk("doc", "docs/workflow.md", "工作流审批",
                        "流程定义 任务 审批 节点 流转 网关 分支")));
        indexService.commitAndRefresh();
    }

    @AfterEach
    void tearDown() throws Exception {
        indexService.destroy();
    }

    @Test
    @DisplayName("写入向量后暴露维度与签名")
    void shouldExposeVectorDimensionAndSignature() {
        assertEquals(3, indexService.count());
        assertEquals(HashEmbeddingProvider.DEFAULT_DIMENSION, indexService.indexedDimension());
        assertEquals("hash:" + HashEmbeddingProvider.DEFAULT_DIMENSION, indexService.indexedSignature());
        assertEquals(HashEmbeddingProvider.DEFAULT_DIMENSION, indexService.stats(3).getDimension());
    }

    @Test
    @DisplayName("向量检索命中语义相关块并返回相似度分数")
    void shouldSearchVector() {
        float[] query = embeddingService.embed("网关路由配置");
        List<SearchHit> hits = indexService.searchVector(query, 3, Set.of());
        assertEquals(3, hits.size());
        assertEquals("deploy/gateway-routes.yml", hits.get(0).getPath());
        assertTrue(hits.get(0).getScore() > hits.get(hits.size() - 1).getScore());
    }

    @Test
    @DisplayName("向量检索支持知识类型过滤")
    void shouldFilterVectorSearchByKind() {
        float[] query = embeddingService.embed("用户登录认证");
        List<SearchHit> hits = indexService.searchVector(query, 3, Set.of("code"));
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().allMatch(hit -> "code".equals(hit.getKind())));
    }

    @Test
    @DisplayName("维度不一致或无向量时向量检索安全返回空")
    void shouldReturnEmptyOnDimensionMismatch() {
        assertTrue(indexService.searchVector(new float[8], 3, Set.of()).isEmpty());
        assertTrue(indexService.searchVector(null, 3, Set.of()).isEmpty());
    }

    @Test
    @DisplayName("BM25 通道在写入向量后仍可用")
    void shouldKeepBm25Working() {
        List<SearchHit> hits = indexService.search("网关路由", 3, Set.of());
        assertFalse(hits.isEmpty());
    }

    @Test
    @DisplayName("权限码过滤：受限知识块仅授权可见，普通块恒可见")
    void shouldFilterByPermissionCode() {
        indexService.addFileChunks("file:900", "hash-9", List.of(
                chunk("archive", "file:900", "秘密档案", "档案 密级 秘密 采购 审批 内容")),
                900L, "sys:archive:secret:read");
        indexService.addFileChunks("file:901", "hash-10", List.of(
                chunk("archive", "file:901", "内部档案", "档案 密级 内部 采购 审批 内容")),
                901L, "sys:archive:item:read");
        indexService.commitAndRefresh();

        List<SearchHit> all = indexService.search("档案 采购 审批", 10, Set.of());
        assertTrue(all.stream().anyMatch(hit -> "docs/workflow.md".equals(hit.getPath())),
                "未启用权限过滤时应命中普通知识块");

        List<SearchHit> anonymous = indexService.search("档案 采购 审批", 10, Set.of(), Set.of());
        assertTrue(anonymous.stream().noneMatch(hit -> hit.getPath().startsWith("file:")),
                "无权限码时不得返回档案知识块");
        assertTrue(anonymous.stream().anyMatch(hit -> "docs/workflow.md".equals(hit.getPath())),
                "普通知识块应保持可见");

        List<SearchHit> itemReader = indexService.search("档案 采购 审批", 10, Set.of(),
                Set.of("sys:archive:item:read"));
        assertTrue(itemReader.stream().anyMatch(hit -> "file:901".equals(hit.getPath())));
        assertFalse(itemReader.stream().anyMatch(hit -> "file:900".equals(hit.getPath())),
                "内部档案权限不得看见秘密级档案");

        List<SearchHit> secretReader = indexService.search("档案 采购 审批", 10, Set.of(),
                Set.of("sys:archive:item:read", "sys:archive:secret:read"));
        SearchHit secret = secretReader.stream()
                .filter(hit -> "file:900".equals(hit.getPath())).findFirst().orElseThrow();
        assertEquals(900L, secret.getFileId());
    }

    /**
     * 构造知识块。
     *
     * @param kind    类型
     * @param path    路径
     * @param title   标题
     * @param content 正文
     * @return 知识块
     */
    private static Chunk chunk(String kind, String path, String title, String content) {
        return Chunk.builder()
                .kind(kind)
                .module("test")
                .path(path)
                .title(title)
                .content(content)
                .startLine(1)
                .endLine(10)
                .wave("")
                .build();
    }
}
