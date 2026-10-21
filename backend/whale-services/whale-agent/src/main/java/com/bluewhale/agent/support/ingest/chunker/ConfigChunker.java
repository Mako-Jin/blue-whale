package com.bluewhale.agent.support.ingest.chunker;

import com.bluewhale.agent.support.ingest.Chunk;
import com.bluewhale.agent.support.ingest.ChunkSupport;
import com.bluewhale.agent.support.ingest.ChunkerRegistry;
import com.bluewhale.agent.support.ingest.DocumentChunker;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置类文件切分器：YAML 按顶层键切分，其余（JSON / XML / properties）整文件行窗切分。
 *
 * <p>网关路由（{@code application.yml}）、服务配置、{@code pom.xml} 版本定义等均可检索；
 * 敏感配置（{@code application-prod*}）已在扫描阶段排除。</p>
 *
 * @author BlueWhale
 */
@Component
public class ConfigChunker implements DocumentChunker {

    /** 支持的扩展名 */
    private static final Set<String> EXTENSIONS = Set.of("yml", "yaml", "json", "xml", "properties");

    /** YAML 顶层键 */
    private static final Pattern YAML_TOP_KEY = Pattern.compile("^([A-Za-z0-9_.\\-]+)\\s*:.*$");

    @Override
    public boolean supports(String path) {
        return EXTENSIONS.contains(ChunkerRegistry.extension(path));
    }

    @Override
    public List<Chunk> chunk(String path, String module, String content) {
        String extension = ChunkerRegistry.extension(path);
        List<String> lines = Arrays.asList(content.split("\n", -1));
        if ("yml".equals(extension) || "yaml".equals(extension)) {
            return chunkYaml(path, module, lines);
        }
        List<Chunk> chunks = new ArrayList<>();
        appendWindows(chunks, path, module, pathFileName(path), lines, 1);
        return chunks;
    }

    /**
     * YAML 按顶层键归集段落。
     *
     * @param path 仓库相对路径
     * @param module 模块
     * @param lines 源行
     * @return 知识块列表
     */
    private List<Chunk> chunkYaml(String path, String module, List<String> lines) {
        List<Chunk> chunks = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        int startLine = 1;
        String title = pathFileName(path);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = YAML_TOP_KEY.matcher(line);
            if (matcher.matches() && !line.startsWith(" ") && !buffer.isEmpty()) {
                appendWindows(chunks, path, module, title, buffer, startLine);
                buffer = new ArrayList<>();
                startLine = i + 1;
            }
            if (matcher.matches() && !line.startsWith(" ")) {
                title = pathFileName(path) + "#" + matcher.group(1);
                if (buffer.isEmpty()) {
                    startLine = i + 1;
                }
            }
            buffer.add(line);
        }
        if (!buffer.isEmpty()) {
            appendWindows(chunks, path, module, title, buffer, startLine);
        }
        return chunks;
    }

    /**
     * 生成行窗知识块。
     *
     * @param chunks 结果列表
     * @param path 仓库相对路径
     * @param module 模块
     * @param title 标题
     * @param lines 源行
     * @param baseLine 起始行号
     */
    private void appendWindows(List<Chunk> chunks, String path, String module, String title,
                               List<String> lines, int baseLine) {
        for (ChunkSupport.Window window : ChunkSupport.splitWindows(
                lines, ChunkSupport.MAX_CHARS, ChunkSupport.OVERLAP_LINES)) {
            if (window.getText().isBlank()) {
                continue;
            }
            chunks.add(Chunk.builder()
                    .kind("config")
                    .module(module)
                    .path(path)
                    .title(title)
                    .content(window.getText())
                    .startLine(baseLine + window.getStartLine() - 1)
                    .endLine(baseLine + window.getEndLine() - 1)
                    .wave(ChunkSupport.extractWave(path + "\n" + window.getText()))
                    .build());
        }
    }

    /**
     * 提取文件名。
     *
     * @param path 仓库相对路径
     * @return 文件名
     */
    private String pathFileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
