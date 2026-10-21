package com.bluewhale.agent.support.ingest.chunker;

import com.bluewhale.agent.support.ingest.Chunk;
import com.bluewhale.agent.support.ingest.ChunkSupport;
import com.bluewhale.agent.support.ingest.ChunkerRegistry;
import com.bluewhale.agent.support.ingest.DocumentChunker;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 切分器：按标题层级切分，保留「标题路径」作为 title。
 *
 * <p>单块超过 {@link ChunkSupport#MAX_CHARS} 时按行窗重叠切分，避免大文档整段不可检索。</p>
 *
 * @author BlueWhale
 */
@Component
public class MarkdownChunker implements DocumentChunker {

    /** 标题行：1–6 个 # + 文本 */
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");

    @Override
    public boolean supports(String path) {
        String extension = ChunkerRegistry.extension(path);
        return "md".equals(extension) || "markdown".equals(extension);
    }

    @Override
    public List<Chunk> chunk(String path, String module, String content) {
        List<String> lines = Arrays.asList(content.split("\n", -1));
        List<Section> sections = splitSections(path, lines);
        List<Chunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            appendChunks(chunks, path, module, section);
        }
        return chunks;
    }

    /**
     * 按标题分层归集段落。
     *
     * @param path 仓库相对路径
     * @param lines 源行
     * @return 段落列表
     */
    private List<Section> splitSections(String path, List<String> lines) {
        List<Section> sections = new ArrayList<>();
        Deque<Heading> stack = new ArrayDeque<>();
        String fallbackTitle = path.substring(path.lastIndexOf('/') + 1);
        List<String> buffer = new ArrayList<>();
        int startLine = 1;
        int index = 0;
        for (String line : lines) {
            Matcher matcher = HEADING.matcher(line);
            if (matcher.matches()) {
                flush(sections, buffer, stack, fallbackTitle, startLine);
                int level = matcher.group(1).length();
                while (!stack.isEmpty() && stack.peekLast().level >= level) {
                    stack.pollLast();
                }
                stack.addLast(new Heading(level, matcher.group(2)));
                buffer = new ArrayList<>();
                startLine = index + 1;
            }
            buffer.add(line);
            index++;
        }
        flush(sections, buffer, stack, fallbackTitle, startLine);
        return sections;
    }

    /**
     * 把缓冲区段落落盘为 Section 并清空缓冲区。
     *
     * @param sections 结果列表
     * @param buffer 缓冲区
     * @param stack 标题栈
     * @param fallbackTitle 无标题时的回退标题
     * @param startLine 段落起始行号
     */
    private void flush(List<Section> sections, List<String> buffer, Deque<Heading> stack,
                       String fallbackTitle, int startLine) {
        if (buffer.isEmpty()) {
            return;
        }
        String title = stack.isEmpty() ? fallbackTitle : titlePath(stack);
        sections.add(new Section(title, startLine, new ArrayList<>(buffer)));
        buffer.clear();
    }

    /**
     * 拼接标题路径（A > B > C）。
     *
     * @param stack 标题栈
     * @return 标题路径
     */
    private String titlePath(Deque<Heading> stack) {
        List<String> parts = new ArrayList<>();
        for (Heading heading : stack) {
            parts.add(heading.text);
        }
        return String.join(" > ", parts);
    }

    /**
     * 段落按字符上限切成知识块。
     *
     * @param chunks 结果列表
     * @param path 仓库相对路径
     * @param module 模块
     * @param section 段落
     */
    private void appendChunks(List<Chunk> chunks, String path, String module, Section section) {
        for (ChunkSupport.Window window : ChunkSupport.splitWindows(
                section.lines, ChunkSupport.MAX_CHARS, ChunkSupport.OVERLAP_LINES)) {
            if (window.getText().isBlank()) {
                continue;
            }
            chunks.add(Chunk.builder()
                    .kind("doc")
                    .module(module)
                    .path(path)
                    .title(section.title)
                    .content(window.getText())
                    .startLine(section.startLine + window.getStartLine() - 1)
                    .endLine(section.startLine + window.getEndLine() - 1)
                    .wave(ChunkSupport.extractWave(section.title + "\n" + window.getText()))
                    .build());
        }
    }

    /**
     * 标题节点。
     *
     * @author BlueWhale
     */
    private record Heading(int level, String text) {
    }

    /**
     * 标题下的段落。
     *
     * @author BlueWhale
     */
    private record Section(String title, int startLine, List<String> lines) {
    }
}
