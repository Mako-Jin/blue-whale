package com.bluewhale.agent.support.ingest.chunker;

import com.bluewhale.agent.support.ingest.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Markdown 切分器测试：标题路径、行号区间、空内容。
 *
 * @author BlueWhale
 */
class MarkdownChunkerTest {

    private final MarkdownChunker chunker = new MarkdownChunker();

    @Test
    @DisplayName("按标题层级生成标题路径与行号")
    void shouldSplitByHeadingPath() {
        String markdown = """
                # 平台
                介绍

                ## 网关
                路由规则
                """;
        List<Chunk> chunks = chunker.chunk("docs/design/a.md", "whale-agent", markdown);
        assertEquals(2, chunks.size());
        assertEquals("平台", chunks.get(0).getTitle());
        assertEquals(1, chunks.get(0).getStartLine());
        assertEquals("平台 > 网关", chunks.get(1).getTitle());
        assertEquals(4, chunks.get(1).getStartLine());
    }

    @Test
    @DisplayName("无标题文档回退文件名标题")
    void shouldFallbackToFileName() {
        List<Chunk> chunks = chunker.chunk("docs/readme.md", "", "纯文本内容");
        assertEquals(1, chunks.size());
        assertEquals("readme.md", chunks.get(0).getTitle());
        assertTrue(chunker.supports("docs/readme.md"));
        assertFalse(chunker.supports("docs/readme.txt"));
    }
}
