package com.bluewhale.agent.support.qa;

import com.bluewhale.agent.config.AgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 引用校验测试：路径存在性、行号裁剪与虚拟来源豁免。
 *
 * @author BlueWhale
 */
class CitationValidatorTest {

    @TempDir
    Path tempDir;

    private CitationValidator validator;

    private final AgentProperties properties = new AgentProperties();

    @BeforeEach
    void setUp() throws IOException {
        properties.setRepoRoot(tempDir.toString());
        Files.writeString(tempDir.resolve("demo.md"), "line1\nline2\nline3\n");
        validator = new CitationValidator(properties);
    }

    @Test
    @DisplayName("丢弃不存在的路径，保留真实文件")
    void dropMissingPath() {
        List<Citation> result = validator.validate(List.of(
                new Citation("S1", "chunk", "demo.md", "demo", 1, 3, "x", null, null, 1.0D),
                new Citation("S2", "chunk", "missing.md", "missing", 1, 3, "y", null, null, 0.5D)));
        assertEquals(1, result.size());
        assertEquals("S1", result.get(0).sourceId());
    }

    @Test
    @DisplayName("行号超出文件实际行数时裁剪")
    void trimLines() {
        List<Citation> result = validator.validate(List.of(
                new Citation("S1", "chunk", "demo.md", "demo", 2, 99, "x", null, null, 1.0D)));
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).startLine());
        assertEquals(3, result.get(0).endLine());
    }

    @Test
    @DisplayName("git/runtime 虚拟来源不做文件存在性校验")
    void virtualSources() {
        List<Citation> result = validator.validate(List.of(
                new Citation("S1", "fact", "git:abc123", "commit", null, null, "x", "COMMIT", "abc123", null),
                new Citation("S2", "fact", "runtime:nacos", "runtime", null, null, "y", "RUNTIME_SERVICE", "x", null)));
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(item -> item.path().startsWith("git:") || item.path().startsWith("runtime:")));
    }
}
