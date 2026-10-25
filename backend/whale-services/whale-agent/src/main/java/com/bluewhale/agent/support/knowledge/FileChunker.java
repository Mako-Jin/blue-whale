package com.bluewhale.agent.support.knowledge;

import com.bluewhale.agent.support.ingest.Chunk;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 业务文件切块器：把抽取文本切为可检索知识块。
 *
 * <p>规则：单块 ≤ 1200 字、相邻块重叠 150 字；优先在换行处断开。
 * 表格类文档（CSV/XLSX）按行成块并重复表头；PPT/PDF 先按分页符（{@code \f}）分页，
 * 再在页内切窗，避免跨页语义粘连。</p>
 *
 * @author BlueWhale
 */
@Component
public class FileChunker {

    /** 单块字符上限 */
    public static final int MAX_CHARS = 1200;

    /** 相邻块重叠字符数 */
    public static final int OVERLAP_CHARS = 150;

    /** 分页符（Tika 对 PDF/PPT 的分页标记） */
    private static final char PAGE_BREAK = '\f';

    /** 表格类扩展名 */
    private static final List<String> TABLE_EXTENSIONS = List.of("csv", "xlsx");

    /** 知识块类型：业务文件 */
    public static final String KIND_FILE = "file";

    /**
     * 切分文件文本。
     *
     * @param path 知识块路径（{@code file:<id>}）
     * @param module 所属模块（桶名）
     * @param title 标题（原始文件名）
     * @param content 抽取文本
     * @return 知识块列表；无有效内容返回空列表
     */
    public List<Chunk> chunk(String path, String module, String title, String content) {
        List<Chunk> chunks = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            return chunks;
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String extension = extensionOf(title);
        if (TABLE_EXTENSIONS.contains(extension)) {
            chunkTable(path, module, title, normalized, chunks);
            return chunks;
        }
        String[] pages = normalized.split("\\f", -1);
        for (String page : pages) {
            window(path, module, title, page, chunks);
        }
        return chunks;
    }

    /**
     * 表格按行切块：首行作为表头，每块重复表头，避免语义丢失。
     *
     * @param path 知识块路径
     * @param module 所属模块
     * @param title 标题
     * @param content 表格文本（行分隔）
     * @param chunks 输出列表
     */
    private void chunkTable(String path, String module, String title, String content, List<Chunk> chunks) {
        String[] lines = content.split("\n", -1);
        if (lines.length <= 1) {
            window(path, module, title, content, chunks);
            return;
        }
        String header = lines[0];
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (!StringUtils.hasText(line)) {
                continue;
            }
            int projected = builder.length() + header.length() + line.length() + 2;
            if (builder.length() > 0 && projected > MAX_CHARS) {
                chunks.add(build(path, module, title, header + "\n" + builder));
                builder.setLength(0);
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        if (builder.length() > 0) {
            chunks.add(build(path, module, title, header + "\n" + builder));
        }
    }

    /**
     * 按字符窗口切块（优先换行处断开，块间重叠）。
     *
     * @param path 知识块路径
     * @param module 所属模块
     * @param title 标题
     * @param text 页内文本
     * @param chunks 输出列表
     */
    private void window(String path, String module, String title, String text, List<Chunk> chunks) {
        String value = text == null ? "" : text.trim();
        int length = value.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + MAX_CHARS, length);
            if (end < length) {
                int newline = value.lastIndexOf('\n', end);
                if (newline > start + MAX_CHARS / 2) {
                    end = newline;
                }
            }
            String piece = value.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(build(path, module, title, piece));
            }
            if (end >= length) {
                break;
            }
            start = Math.max(end - OVERLAP_CHARS, start + 1);
        }
    }

    /**
     * 构造知识块。
     *
     * @param path 知识块路径
     * @param module 所属模块
     * @param title 标题
     * @param content 块正文
     * @return 知识块
     */
    private Chunk build(String path, String module, String title, String content) {
        return Chunk.builder()
                .kind(KIND_FILE)
                .module(module)
                .path(path)
                .title(title)
                .content(content)
                .startLine(0)
                .endLine(0)
                .wave("")
                .build();
    }

    /**
     * 提取小写扩展名。
     *
     * @param fileName 文件名
     * @return 扩展名；无扩展名返回空串
     */
    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
