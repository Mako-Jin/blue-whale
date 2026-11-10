package com.bluewhale.agent.support.index;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.service.EmbeddingService;
import com.bluewhale.agent.support.ingest.Chunk;
import com.bluewhale.agent.support.knowledge.KnowledgePermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 归属人隔离测试（方案 B）：个人文件知识仅上传者可见，公共知识对所有用户可见。
 *
 * @author BlueWhale
 */
class AgentIndexOwnerTest {

    /** 测试用权限码（普通文件知识块仅要求问答权限） */
    private static final Set<String> CHAT_PERMISSION = Set.of("sys:agent:chat");

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
    }

    @AfterEach
    void tearDown() throws Exception {
        KnowledgePermissionContext.clear();
        indexService.destroy();
    }

    @Test
    @DisplayName("个人文件仅归属人可检索")
    void shouldHidePersonalFileFromOtherUsers() {
        indexService.addFileChunksWithOwner("file:701", "owner-1", List.of(
                chunk("file", "file:701", "个人报销制度", "个人 报销 制度 附件 说明")), 701L, null, 7L);
        indexService.addFileChunksWithOwner("file:702", "owner-2", List.of(
                chunk("file", "file:702", "同事报销制度", "同事 报销 制度 附件 说明")), 702L, null, 8L);
        indexService.commitAndRefresh();

        KnowledgePermissionContext.set(CHAT_PERMISSION, 7L);
        List<SearchHit> owner7 = indexService.search("报销 制度 附件 说明", 10, Set.of(), CHAT_PERMISSION);
        assertTrue(owner7.stream().anyMatch(hit -> "file:701".equals(hit.getPath())), "归属人应检索到自己的文件");
        assertFalse(owner7.stream().anyMatch(hit -> "file:702".equals(hit.getPath())),
                "归属人不得检索到他人文件");

        KnowledgePermissionContext.set(CHAT_PERMISSION, 8L);
        List<SearchHit> owner8 = indexService.search("报销 制度 附件 说明", 10, Set.of(), CHAT_PERMISSION);
        assertTrue(owner8.stream().anyMatch(hit -> "file:702".equals(hit.getPath())));
        assertFalse(owner8.stream().anyMatch(hit -> "file:701".equals(hit.getPath())));
    }

    @Test
    @DisplayName("无身份链路仅返回无归属人的公共知识")
    void shouldOnlyExposePublicKnowledgeWithoutOwner() {
        indexService.addFileChunksWithOwner("file:701", "owner-1", List.of(
                chunk("file", "file:701", "个人制度草稿", "个人 制度 草稿 附件")), 701L, null, 7L);
        indexService.addFileChunks("public/notice.md", "hash-pub", List.of(
                chunk("doc", "public/notice.md", "公开通知", "公开 通知 制度 附件")));
        indexService.commitAndRefresh();

        KnowledgePermissionContext.clear();
        List<SearchHit> anonymous = indexService.search("制度 附件 草稿 通知", 10, Set.of(), Set.of());
        assertTrue(anonymous.stream().anyMatch(hit -> "public/notice.md".equals(hit.getPath())),
                "公共知识对无身份链路可见");
        assertFalse(anonymous.stream().anyMatch(hit -> "file:701".equals(hit.getPath())),
                "无身份链路不得看到个人文件");
    }

    @Test
    @DisplayName("向量通道同样按归属人过滤")
    void shouldFilterVectorByOwner() {
        indexService.addFileChunksWithOwner("file:701", "owner-1", List.of(
                chunk("file", "file:701", "个人制度", "个人 制度 附件 说明")), 701L, null, 7L);
        indexService.addFileChunksWithOwner("file:702", "owner-2", List.of(
                chunk("file", "file:702", "同事制度", "同事 制度 附件 说明")), 702L, null, 8L);
        indexService.commitAndRefresh();

        float[] query = embeddingService.embed("制度 附件 说明");
        KnowledgePermissionContext.set(CHAT_PERMISSION, 7L);
        List<SearchHit> owner7 = indexService.searchVector(query, 10, Set.of(), CHAT_PERMISSION);
        assertTrue(owner7.stream().anyMatch(hit -> "file:701".equals(hit.getPath())));
        assertFalse(owner7.stream().anyMatch(hit -> "file:702".equals(hit.getPath())),
                "向量通道不得召回他人个人文件");
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
