package com.bluewhale.agent.support.knowledge;

import com.bluewhale.agent.support.ingest.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务文件切块器测试：块长上限、重叠、表格表头与分页。
 *
 * @author BlueWhale
 */
class FileChunkerTest {

    private final FileChunker chunker = new FileChunker();

    @Test
    @DisplayName("长文本按 1200 字切块并保留 150 字重叠")
    void shouldWindowLongText() {
        String content = "a".repeat(3000);
        List<Chunk> chunks = chunker.chunk("file:1", "whale-file", "说明.txt", content);
        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).getContent().length() <= FileChunker.MAX_CHARS);
        assertEquals(FileChunker.MAX_CHARS, chunks.get(0).getContent().length());
        assertEquals("file", chunks.get(0).getKind());
        assertEquals("说明.txt", chunks.get(0).getTitle());
        assertEquals("file:1", chunks.get(0).getPath());
        assertEquals("whale-file", chunks.get(0).getModule());
    }

    @Test
    @DisplayName("档案快照切块使用 archive 类型")
    void shouldUseArchiveKind() {
        List<Chunk> chunks = chunker.chunk("file:9", "whale-archive", "archive-WORKFLOW-1.json",
                "{\"instance\":{\"title\":\"采购审批\"}}", FileChunker.KIND_ARCHIVE);
        assertEquals(1, chunks.size());
        assertEquals(FileChunker.KIND_ARCHIVE, chunks.get(0).getKind());
        assertEquals("whale-archive", chunks.get(0).getModule());
    }

    @Test
    @DisplayName("表格按行切块且每块重复表头")
    void shouldRepeatHeaderForTable() {
        StringBuilder builder = new StringBuilder("姓名,部门\n");
        for (int i = 0; i < 60; i++) {
            builder.append("员工").append(i).append(",研发部门负责平台研发与运维保障工作\n");
        }
        List<Chunk> chunks = chunker.chunk("file:2", "whale-file", "花名册.csv", builder.toString());
        assertTrue(chunks.size() > 1);
        for (Chunk chunk : chunks) {
            assertTrue(chunk.getContent().startsWith("姓名,部门"));
            assertTrue(chunk.getContent().length() <= FileChunker.MAX_CHARS);
        }
        assertTrue(chunks.get(chunks.size() - 1).getContent().contains("员工59"));
    }

    @Test
    @DisplayName("分页符拆分为独立知识块")
    void shouldSplitPages() {
        List<Chunk> chunks = chunker.chunk("file:3", "whale-file", "汇报.pptx", "第一页\f第二页");
        assertEquals(2, chunks.size());
        assertEquals("第一页", chunks.get(0).getContent());
        assertEquals("第二页", chunks.get(1).getContent());
    }

    @Test
    @DisplayName("空文本返回空块列表")
    void shouldReturnEmptyForBlank() {
        assertTrue(chunker.chunk("file:4", "whale-file", "空.txt", "  ").isEmpty());
    }
}
