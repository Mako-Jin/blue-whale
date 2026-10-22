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
 * TypeScript / JavaScript 切分器：按顶层 {@code export} 声明切分组件、函数、类型。
 *
 * <p>组件文件（tsx/vue）通常单文件较大，仍按字符上限行窗重叠切分；标题取声明名。</p>
 *
 * @author BlueWhale
 */
@Component
public class TypeScriptChunker implements DocumentChunker {

    /** 支持的扩展名 */
    private static final Set<String> EXTENSIONS = Set.of("ts", "tsx", "js", "jsx", "vue");

    /** 顶层声明：export [default] [async] function|const|let|var|class|interface|type|enum NAME */
    private static final Pattern DECLARATION = Pattern.compile(
            "^(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?(function|const|let|var|class|interface|type|enum)\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)");

    @Override
    public boolean supports(String path) {
        return EXTENSIONS.contains(ChunkerRegistry.extension(path));
    }

    @Override
    public List<Chunk> chunk(String path, String module, String content) {
        List<String> lines = Arrays.asList(content.split("\n", -1));
        List<Unit> units = parseUnits(lines);
        List<Chunk> chunks = new ArrayList<>();
        if (units.isEmpty()) {
            appendWindows(chunks, path, module, pathFileName(path), lines, 1);
            return chunks;
        }
        for (Unit unit : units) {
            appendWindows(chunks, path, module, unit.title, unit.lines(), unit.startLine);
        }
        return chunks;
    }

    /**
     * 解析顶层声明单元。
     *
     * @param lines 源行
     * @return 单元列表
     */
    private List<Unit> parseUnits(List<String> lines) {
        List<Unit> units = new ArrayList<>();
        int depth = 0;
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            Matcher matcher = DECLARATION.matcher(line.trim());
            if (depth == 0 && matcher.find()) {
                int end = findUnitEnd(lines, index, depth);
                units.add(new Unit(matcher.group(2), index, end, lines.subList(index, end + 1)));
                depth += braceDelta(lines.get(index));
                index = Math.max(end + 1, index + 1);
                depth = 0;
                continue;
            }
            depth += braceDelta(line);
            index++;
        }
        return units;
    }

    /**
     * 从声明行起找到单元结束行。
     *
     * @param lines 源行
     * @param start 起始下标
     * @param startDepth 起始深度
     * @return 结束下标（含）
     */
    private int findUnitEnd(List<String> lines, int start, int startDepth) {
        int depth = startDepth;
        boolean opened = false;
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("{")) {
                opened = true;
            }
            depth += braceDelta(line);
            if (opened && depth <= startDepth) {
                return i;
            }
            if (!opened && line.trim().endsWith(";")) {
                return i;
            }
        }
        return lines.size() - 1;
    }

    /**
     * 单行花括号净值（忽略字符串与注释的近似统计）。
     *
     * @param line 源码行
     * @return 净值
     */
    private int braceDelta(String line) {
        String sanitized = line.replaceAll("\"[^\"]*\"", "").replaceAll("'[^']*'", "")
                .replaceAll("`[^`]*`", "").replaceAll("//.*$", "");
        int delta = 0;
        for (int i = 0; i < sanitized.length(); i++) {
            char ch = sanitized.charAt(i);
            if (ch == '{') {
                delta++;
            } else if (ch == '}') {
                delta--;
            }
        }
        return delta;
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
                    .kind("code")
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

    /**
     * 源码单元。
     *
     * @param title 单元标题
     * @param startLine 起始行号
     * @param endLine 结束行号
     * @param lines 单元源行
     * @author BlueWhale
     */
    private record Unit(String title, int startLine, int endLine, List<String> lines) {
    }
}
