package com.bluewhale.agent.support.mcp;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.entity.AgentFact;
import com.bluewhale.agent.service.AgentSearchService;
import com.bluewhale.agent.service.FactStoreService;
import com.bluewhale.agent.support.index.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MCP 只读工具测试：文件读取边界、运行时事实隔离与检索映射。
 *
 * @author BlueWhale
 */
class McpToolServiceTest {

    @TempDir
    Path repoRoot;

    private AgentSearchService searchService;

    private FactStoreService factStoreService;

    private McpToolService toolService;

    @BeforeEach
    void setUp() {
        searchService = mock(AgentSearchService.class);
        factStoreService = mock(FactStoreService.class);
        AgentProperties properties = new AgentProperties();
        properties.setRepoRoot(repoRoot.toString());
        toolService = new McpToolService(searchService, factStoreService, properties);
    }

    @Test
    void readFileReturnsRequestedLineRange() throws IOException {
        Path nested = Files.createDirectories(repoRoot.resolve("nested"));
        Files.writeString(nested.resolve("a.txt"), "l1\nl2\nl3\nl4\nl5\n", StandardCharsets.UTF_8);

        Map<String, Object> result = toolService.readFile(
                Map.of("path", "nested/a.txt", "startLine", 2, "endLine", 3));

        assertEquals("l2\nl3", result.get("content"));
        assertEquals(5, result.get("totalLines"));
        assertEquals(2, result.get("startLine"));
        assertEquals(3, result.get("endLine"));
        assertEquals(false, result.get("truncated"));
    }

    @Test
    void readFileRejectsTraversalAndAbsolutePath() throws IOException {
        Files.writeString(repoRoot.resolve("inside.txt"), "ok", StandardCharsets.UTF_8);
        Path outside = Files.createTempFile("mcp-outside", ".txt");
        Files.writeString(outside, "secret", StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> toolService.readFile(
                Map.of("path", "../" + outside.getFileName())));
        assertThrows(IllegalArgumentException.class, () -> toolService.readFile(
                Map.of("path", outside.toString())));
    }

    @Test
    void readFileRejectsEnvAndBuildOutput() throws IOException {
        Files.writeString(repoRoot.resolve(".env"), "MYSQL_PASSWORD=x", StandardCharsets.UTF_8);
        Path target = Files.createDirectories(repoRoot.resolve("backend/mock/target"));
        Files.writeString(target.resolve("a.txt"), "artifact", StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> toolService.readFile(Map.of("path", ".env")));
        assertThrows(IllegalArgumentException.class, () -> toolService.readFile(
                Map.of("path", "backend/mock/target/a.txt")));
    }

    @Test
    void getFactRejectsRuntimeFacts() {
        assertThrows(IllegalArgumentException.class, () -> toolService.getFact(
                Map.of("type", FactStoreService.RUNTIME_SERVICE)));
    }

    @Test
    void searchKnowledgeMapsHitsAndClampsTopK() {
        when(searchService.search(anyString(), anyInt(), any())).thenReturn(List.of(
                SearchHit.builder().path("docs/service-catalog.md").startLine(10).endLine(20)
                        .kind("doc").title("服务目录").content("网关路由").score(1.5F).build()));

        Map<String, Object> result = toolService.searchKnowledge(
                Map.of("query", "网关路由", "topK", 999));

        assertEquals(1, result.get("count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals("docs/service-catalog.md", items.get(0).get("path"));
        assertEquals(10, items.get(0).get("startLine"));
    }

    @Test
    void listServicesReturnsServiceAndPortFacts() {
        AgentFact service = new AgentFact();
        service.setFactType("SERVICE");
        service.setFactKey("whale-agent");
        service.setValueJson("{\"port\":8090}");
        when(factStoreService.query(eq("SERVICE"), isNull(), anyInt(), eq(false)))
                .thenReturn(List.of(service));
        when(factStoreService.query(eq("PORT"), isNull(), anyInt(), eq(false))).thenReturn(List.of());
        when(factStoreService.parseValue(anyString())).thenReturn(Map.of("port", 8090));

        Map<String, Object> result = toolService.listServices();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) result.get("services");
        assertEquals(1, services.size());
        assertEquals("whale-agent", services.get(0).get("key"));
        assertTrue(((List<?>) result.get("ports")).isEmpty());
    }
}
