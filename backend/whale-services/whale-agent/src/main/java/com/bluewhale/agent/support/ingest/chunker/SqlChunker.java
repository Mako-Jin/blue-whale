package com.bluewhale.agent.support.ingest.chunker;

import com.bluewhale.agent.support.ingest.Chunk;
import com.bluewhale.agent.support.ingest.ChunkSupport;
import com.bluewhale.agent.support.ingest.ChunkerRegistry;
import com.bluewhale.agent.support.ingest.DocumentChunker;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 切分器：按语句（分号边界）切分，{@code CREATE/ALTER TABLE} 语句以表名为标题。
 *
 * <p>DDL 语句同时会被事实层解析为 {@code TABLE/COLUMN} 事实，本切分器只负责文本可检索。</p>
 *
 * @author BlueWhale
 */
@Component
public class SqlChunker implements DocumentChunker {

    /** CREATE/ALTER TABLE 表名 */
    private static final Pattern TABLE_NAME = Pattern.compile(
            "(?i)\\b(?:create|alter)\\s+table\\s+(?:if\\s+(?:not\\s+)?exists\\s+)?[`\"]?([A-Za-z0-9_]+)[`\"]?");

    @Override
    public boolean supports(String path) {
        return "sql".equals(ChunkerRegistry.extension(path));
    }

    @Override
    public List<Chunk> chunk(String path, String module, String content) {
        List<String> lines = Arrays.asList(content.split("\n", -1));
        List<Chunk> chunks = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        int startLine = 1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (buffer.isEmpty() && line.isBlank()) {
                startLine = i + 2;
                continue;
            }
            buffer.add(line);
            if (line.trim().endsWith(";")) {
                appendStatement(chunks, path, module, buffer, startLine);
                buffer = new ArrayList<>();
                startLine = i + 2;
            }
        }
        if (!buffer.isEmpty()) {
            appendStatement(chunks, path, module, buffer, startLine);
        }
        return chunks;
    }

    /**
     * 落盘单条语句为知识块。
     *
     * @param chunks 结果列表
     * @param path 仓库相对路径
     * @param module 模块
     * @param lines 语句行
     * @param startLine 起始行号
     */
    private void appendStatement(List<Chunk> chunks, String path, String module, List<String> lines, int startLine) {
        String text = String.join("\n", lines);
        if (text.isBlank()) {
            return;
        }
        String title = statementTitle(text, path);
        for (ChunkSupport.Window window : ChunkSupport.splitWindows(
                lines, ChunkSupport.MAX_CHARS, ChunkSupport.OVERLAP_LINES)) {
            chunks.add(Chunk.builder()
                    .kind("sql")
                    .module(module)
                    .path(path)
                    .title(title)
                    .content(window.getText())
                    .startLine(startLine + window.getStartLine() - 1)
                    .endLine(startLine + window.getEndLine() - 1)
                    .wave(ChunkSupport.extractWave(window.getText()))
                    .build());
        }
    }

    /**
     * 语句标题：表名优先，其次首个非注释片段。
     *
     * @param text 语句文本
     * @param path 仓库相对路径
     * @return 标题
     */
    private String statementTitle(String text, String path) {
        Matcher matcher = TABLE_NAME.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                return trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed;
            }
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
