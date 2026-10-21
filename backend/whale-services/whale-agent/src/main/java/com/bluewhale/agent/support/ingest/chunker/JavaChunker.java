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
 * Java 切分器：按「类头 + 类/接口方法」切分，title 形如 {@code ClassName#method}。
 *
 * <p>采用花括号深度 + 声明行正则的轻量解析，不引入完整 AST 解析器；
 * 无法识别声明时回落整文件行窗切分，保证任何文件都可检索。</p>
 *
 * @author BlueWhale
 */
@Component
public class JavaChunker implements DocumentChunker {

    /** 类型声明：class / interface / enum / record */
    private static final Pattern TYPE_DECL = Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    /** 方法名提取 */
    private static final Pattern METHOD_NAME = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");

    /** 非方法声明的控制关键字 */
    private static final Pattern CONTROL = Pattern.compile("^(if|for|while|switch|catch|try|return|new)\\b");

    @Override
    public boolean supports(String path) {
        return "java".equals(ChunkerRegistry.extension(path));
    }

    @Override
    public List<Chunk> chunk(String path, String module, String content) {
        List<String> lines = Arrays.asList(content.split("\n", -1));
        List<Unit> units = parseUnits(lines);
        List<Chunk> chunks = new ArrayList<>();
        if (units.isEmpty()) {
            appendWindows(chunks, path, module, "java", pathFileName(path), lines, 1);
            return chunks;
        }
        for (Unit unit : units) {
            appendWindows(chunks, path, module, "code", unit.title, unit.lines(), unit.startLine);
        }
        return chunks;
    }

    /**
     * 解析类型与方法单元。
     *
     * @param lines 源行
     * @return 单元列表
     */
    private List<Unit> parseUnits(List<String> lines) {
        List<Unit> units = new ArrayList<>();
        int depth = 0;
        String className = "";
        int classStart = -1;
        int classEnd = -1;
        List<int[]> methodRanges = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (depth == 0 && classStart < 0) {
                Matcher typeMatcher = TYPE_DECL.matcher(line);
                if (typeMatcher.find() && !line.trim().startsWith("*")) {
                    className = typeMatcher.group(2);
                    classStart = i;
                }
            } else if (depth == 1 && classStart >= 0 && isMethodDeclaration(line)) {
                int end = findUnitEnd(lines, i, depth);
                String name = methodName(line, className);
                methodRanges.add(new int[]{i, end});
                units.add(new Unit(className + "#" + name, i, end, lines.subList(i, end + 1)));
            }
            depth += braceDelta(line);
            if (classStart >= 0 && classEnd < 0 && depth <= 0 && i > classStart) {
                classEnd = i;
            }
        }
        if (classStart >= 0) {
            int headerEnd = methodRanges.isEmpty() ? (classEnd < 0 ? lines.size() - 1 : classEnd)
                    : methodRanges.get(0)[0] - 1;
            if (headerEnd >= classStart) {
                units.add(0, new Unit(className, classStart, headerEnd, lines.subList(classStart, headerEnd + 1)));
            }
        }
        return units;
    }

    /**
     * 判断是否为方法/构造器声明行（深度 1）。
     *
     * @param line 源码行
     * @return 是方法声明返回 {@code true}
     */
    private boolean isMethodDeclaration(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*")
                || trimmed.startsWith("@") || CONTROL.matcher(trimmed).find()) {
            return false;
        }
        return trimmed.contains("(") && (trimmed.endsWith("{") || trimmed.endsWith(")")
                || trimmed.endsWith(";") || trimmed.contains(") {"));
    }

    /**
     * 提取方法名（无法提取时回落 {@code block}）。
     *
     * @param line 声明行
     * @param className 当前类名
     * @return 方法名
     */
    private String methodName(String line, String className) {
        Matcher matcher = METHOD_NAME.matcher(line);
        if (matcher.find()) {
            String name = matcher.group(1);
            if (name.equals(className)) {
                return name + "()";
            }
            return name;
        }
        return "block";
    }

    /**
     * 从声明行起找到单元结束行（花括号回到起始深度）。
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
                .replaceAll("//.*$", "");
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
     * @param kind 知识类型
     * @param title 标题
     * @param lines 源行
     * @param baseLine 起始行号
     */
    private void appendWindows(List<Chunk> chunks, String path, String module, String kind, String title,
                               List<String> lines, int baseLine) {
        for (ChunkSupport.Window window : ChunkSupport.splitWindows(
                lines, ChunkSupport.MAX_CHARS, ChunkSupport.OVERLAP_LINES)) {
            if (window.getText().isBlank()) {
                continue;
            }
            chunks.add(Chunk.builder()
                    .kind(kind)
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
     * @author BlueWhale
     */
    private record Unit(String title, int startLine, int endLine, List<String> lines) {
    }
}
