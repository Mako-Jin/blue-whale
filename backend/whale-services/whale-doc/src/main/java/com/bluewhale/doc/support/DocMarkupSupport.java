package com.bluewhale.doc.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档正文与 Markdown / HTML 的双向转换（M3 导入导出）。
 *
 * <p>导出以 TipTap JSON 为权威源，服务端递归生成文本；导入仅支持受控子集
 * （标题、段落、列表、代码块、引用、表格、分隔线及常见行内标记），避免引入富文本攻击面。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DocMarkupSupport {

    /** 文档节点类型 */
    private static final String TYPE_DOC = "doc";

    /** Markdown 标题模式 */
    private static final Pattern MD_HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");

    /** Markdown 无序列表模式 */
    private static final Pattern MD_BULLET = Pattern.compile("^\\s*[-*+]\\s+(.*)$");

    /** Markdown 有序列表模式 */
    private static final Pattern MD_ORDERED = Pattern.compile("^\\s*\\d+\\.\\s+(.*)$");

    /** Markdown 分隔线模式 */
    private static final Pattern MD_RULE = Pattern.compile("^\\s*([-*_]\\s*){3,}$");

    /** Markdown 行内标记模式 */
    private static final Pattern INLINE = Pattern.compile(
            "\\*\\*([^*]+)\\*\\*|\\*([^*]+)\\*|`([^`]+)`|~~([^~]+)~~|\\[([^]]*)]\\(([^)]*)\\)");

    /** HTML 块级标签模式 */
    private static final Pattern HTML_BLOCK = Pattern.compile(
            "<(h[1-6]|p|li|pre|blockquote|br|hr|ul|ol)(?:\\s+[^>]*)?>|</(h[1-6]|p|li|pre|blockquote|ul|ol)>",
            Pattern.CASE_INSENSITIVE);

    /** URL 协议前缀模式 */
    private static final Pattern SCHEME = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*):");

    private final ObjectMapper objectMapper;

    /**
     * 将 TipTap JSON 导出为 Markdown。
     *
     * @param contentJson 正文 JSON
     * @return Markdown 文本；空正文返回空串
     */
    public String toMarkdown(String contentJson) {
        JsonNode root = parse(contentJson);
        if (root == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        writeBlocks(root.path("content"), builder, 0);
        return normalize(builder.toString());
    }

    /**
     * 将 TipTap JSON 导出为转义后的 HTML。
     *
     * @param contentJson 正文 JSON
     * @return HTML 文本；空正文返回空串
     */
    public String toHtml(String contentJson) {
        JsonNode root = parse(contentJson);
        if (root == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        writeHtml(root.path("content"), builder);
        return builder.toString();
    }

    /**
     * 将 Markdown 文本导入为 TipTap JSON。
     *
     * @param markdown Markdown 文本
     * @return TipTap JSON 字符串
     */
    public String fromMarkdown(String markdown) {
        List<Object> blocks = new ArrayList<>();
        List<String> lines = List.of(markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            if (line.isBlank()) {
                index++;
                continue;
            }
            if (line.trim().startsWith("```")) {
                index = readCodeBlock(lines, index, blocks);
                continue;
            }
            Matcher heading = MD_HEADING.matcher(line.trim());
            if (heading.matches()) {
                blocks.add(block("heading", Map.of("level", heading.group(1).length()),
                        inline(heading.group(2).trim())));
                index++;
                continue;
            }
            if (MD_RULE.matcher(line).matches()) {
                blocks.add(block("horizontalRule", Map.of(), null));
                index++;
                continue;
            }
            if (line.trim().startsWith(">")) {
                index = readQuote(lines, index, blocks);
                continue;
            }
            if (MD_BULLET.matcher(line).matches()) {
                index = readList(lines, index, blocks, false);
                continue;
            }
            if (MD_ORDERED.matcher(line).matches()) {
                index = readList(lines, index, blocks, true);
                continue;
            }
            if (line.trim().startsWith("|")) {
                index = readTable(lines, index, blocks);
                continue;
            }
            index = readParagraph(lines, index, blocks);
        }
        return stringify(blocks);
    }

    /**
     * 将简单 HTML 文本导入为 TipTap JSON（不保留样式与脚本）。
     *
     * @param html HTML 文本
     * @return TipTap JSON 字符串
     */
    public String fromHtml(String html) {
        String source = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "")
                .replace("\r\n", "\n");
        List<Object> blocks = new ArrayList<>();
        Matcher matcher = HTML_BLOCK.matcher(source);
        String pending = "";
        String block = null;
        int start = 0;
        while (matcher.find()) {
            String text = stripInline(source.substring(start, matcher.start()));
            start = matcher.end();
            String tag = matcher.group(1) != null ? matcher.group(1).toLowerCase() : matcher.group(2).toLowerCase();
            boolean closing = matcher.group(2) != null;
            if (tag.equals("br") || tag.equals("hr")) {
                flushText(blocks, pending + " " + text);
                pending = "";
                if (tag.equals("hr")) {
                    blocks.add(block("horizontalRule", Map.of(), null));
                }
                continue;
            }
            if (closing) {
                if (block != null && block.equals(tag)) {
                    blocks.addAll(closeHtmlBlock(tag, pending + " " + text));
                    pending = "";
                    block = null;
                } else {
                    pending += " " + text;
                }
                continue;
            }
            if (block == null) {
                pending += " " + text;
                block = tag;
            } else {
                pending += " " + text;
            }
        }
        flushText(blocks, pending + " " + stripInline(source.substring(start)));
        return stringify(blocks);
    }

    /**
     * 解析正文 JSON 根节点。
     *
     * @param contentJson 正文 JSON
     * @return 根节点；非法或空返回 {@code null}
     */
    private JsonNode parse(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(contentJson);
            return node.isObject() ? node : null;
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "正文 JSON 无法解析");
        }
    }

    /**
     * 递归输出 Markdown 块。
     *
     * @param nodes  节点数组
     * @param out    输出缓冲
     * @param indent 缩进层级
     */
    private void writeBlocks(JsonNode nodes, StringBuilder out, int indent) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            writeBlock(node, out, indent);
        }
    }

    /**
     * 输出单个 Markdown 块。
     *
     * @param node   节点
     * @param out    输出缓冲
     * @param indent 缩进层级
     */
    private void writeBlock(JsonNode node, StringBuilder out, int indent) {
        String type = node.path("type").asText("");
        String prefix = "  ".repeat(Math.max(indent, 0));
        switch (type) {
            case "heading" -> out.append("#".repeat(Math.max(1, node.path("attrs").path("level").asInt(1))))
                    .append(' ').append(mdInline(node.path("content"))).append("\n\n");
            case "paragraph" -> {
                String text = mdInline(node.path("content")).trim();
                if (!text.isEmpty()) {
                    out.append(text).append("\n\n");
                }
            }
            case "codeBlock" -> out.append(prefix).append("```")
                    .append(node.path("attrs").path("language").asText("")).append('\n')
                    .append(mdInline(node.path("content"))).append("\n").append(prefix).append("```\n\n");
            case "blockquote" -> {
                String text = mdInline(node.path("content")).trim();
                out.append(text.lines().map(line -> "> " + line).reduce((a, b) -> a + "\n" + b).orElse(">" ))
                        .append("\n\n");
            }
            case "horizontalRule" -> out.append(prefix).append("---\n\n");
            case "bulletList", "orderedList" -> writeList(node, out, indent, "orderedList".equals(type));
            case "table" -> writeTable(node, out);
            case "image" -> out.append(prefix).append("![").append(node.path("attrs").path("alt").asText(""))
                    .append("](").append(safeImageSrc(node.path("attrs").path("src").asText(""))).append(")\n\n");
            case "listItem" -> writeBlocks(node.path("content"), out, indent);
            default -> writeBlocks(node.path("content"), out, indent);
        }
    }

    /**
     * 输出 Markdown 列表。
     *
     * @param node    列表节点
     * @param out     输出缓冲
     * @param indent  缩进层级
     * @param ordered 是否有序列表
     */
    private void writeList(JsonNode node, StringBuilder out, int indent, boolean ordered) {
        String prefix = "  ".repeat(Math.max(indent, 0));
        int index = 1;
        for (JsonNode item : node.path("content")) {
            String marker = ordered ? index++ + ". " : "- ";
            String text = mdInline(item.path("content")).trim();
            out.append(prefix).append(marker).append(text).append('\n');
            writeBlocks(item.path("content"), out, indent + 1);
        }
        out.append('\n');
    }

    /**
     * 输出 Markdown 表格。
     *
     * @param node 表格节点
     * @param out  输出缓冲
     */
    private void writeTable(JsonNode node, StringBuilder out) {
        List<JsonNode> rows = new ArrayList<>();
        node.path("content").forEach(rows::add);
        for (int i = 0; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            List<String> cells = new ArrayList<>();
            row.path("content").forEach(cell -> cells.add(mdInline(cell.path("content")).trim()));
            out.append("| ").append(String.join(" | ", cells)).append(" |\n");
            if (i == 0) {
                out.append('|').append(" --- |".repeat(Math.max(cells.size(), 1))).append('\n');
            }
        }
        out.append('\n');
    }

    /**
     * 输出行内 Markdown。
     *
     * @param nodes 节点数组或块节点
     * @return Markdown 文本
     */
    private String mdInline(JsonNode nodes) {
        StringBuilder out = new StringBuilder();
        for (JsonNode node : nodes) {
            String type = node.path("type").asText("");
            if ("text".equals(type)) {
                out.append(applyMarks(node.path("text").asText(""), node.path("marks")));
            } else if ("hardBreak".equals(type)) {
                out.append('\n');
            } else if ("image".equals(type)) {
                out.append("![").append(node.path("attrs").path("alt").asText(""))
                        .append("](").append(safeImageSrc(node.path("attrs").path("src").asText(""))).append(")");
            } else if (node.path("content").isArray()) {
                out.append(mdInline(node.path("content")));
            }
        }
        return out.toString();
    }

    /**
     * 为文本套用 Markdown 行内标记。
     *
     * @param text  文本
     * @param marks 标记数组
     * @return 带标记文本
     */
    private String applyMarks(String text, JsonNode marks) {
        String result = text;
        for (JsonNode mark : marks) {
            switch (mark.path("type").asText("")) {
                case "bold" -> result = "**" + result + "**";
                case "italic" -> result = "*" + result + "*";
                case "code" -> result = "`" + result + "`";
                case "strike" -> result = "~~" + result + "~~";
                case "link" -> {
                    String href = safeLinkHref(mark.path("attrs").path("href").asText(""));
                    if (!href.isEmpty()) {
                        result = "[" + result + "](" + href + ")";
                    }
                }
                default -> {
                }
            }
        }
        return result;
    }

    /**
     * 递归输出 HTML。
     *
     * @param nodes 节点数组
     * @param out   输出缓冲
     */
    private void writeHtml(JsonNode nodes, StringBuilder out) {
        for (JsonNode node : nodes) {
            String type = node.path("type").asText("");
            switch (type) {
                case "heading" -> out.append("<h").append(node.path("attrs").path("level").asInt(1)).append('>')
                        .append(htmlInline(node.path("content"))).append("</h")
                        .append(node.path("attrs").path("level").asInt(1)).append(">\n");
                case "paragraph" -> out.append("<p>").append(htmlInline(node.path("content"))).append("</p>\n");
                case "codeBlock" -> out.append("<pre><code>").append(escape(mdInline(node.path("content"))))
                        .append("</code></pre>\n");
                case "blockquote" -> out.append("<blockquote>").append(htmlInline(node.path("content")))
                        .append("</blockquote>\n");
                case "horizontalRule" -> out.append("<hr/>\n");
                case "bulletList" -> out.append("<ul>").append(htmlList(node)).append("</ul>\n");
                case "orderedList" -> out.append("<ol>").append(htmlList(node)).append("</ol>\n");
                case "table" -> out.append("<table>").append(htmlTable(node)).append("</table>\n");
                case "image" -> out.append("<img src=\"").append(escape(safeImageSrc(node.path("attrs").path("src").asText(""))))
                        .append("\" alt=\"").append(escape(node.path("attrs").path("alt").asText(""))).append("\"/>\n");
                default -> writeHtml(node.path("content"), out);
            }
        }
    }

    /**
     * 输出 HTML 列表项。
     *
     * @param node 列表节点
     * @return 列表项 HTML
     */
    private String htmlList(JsonNode node) {
        StringBuilder out = new StringBuilder();
        for (JsonNode item : node.path("content")) {
            out.append("<li>").append(htmlInline(item.path("content")));
            for (JsonNode child : item.path("content")) {
                if (child.path("type").asText("").endsWith("List")) {
                    String tag = "orderedList".equals(child.path("type").asText("")) ? "ol" : "ul";
                    out.append('<').append(tag).append('>').append(htmlList(child))
                            .append("</").append(tag).append('>');
                }
            }
            out.append("</li>");
        }
        return out.toString();
    }

    /**
     * 输出 HTML 表格。
     *
     * @param node 表格节点
     * @return 表格 HTML
     */
    private String htmlTable(JsonNode node) {
        StringBuilder out = new StringBuilder();
        for (JsonNode row : node.path("content")) {
            out.append("<tr>");
            for (JsonNode cell : row.path("content")) {
                out.append("<td>").append(htmlInline(cell.path("content"))).append("</td>");
            }
            out.append("</tr>");
        }
        return out.toString();
    }

    /**
     * 输出行内 HTML。
     *
     * @param nodes 节点数组
     * @return HTML 文本
     */
    private String htmlInline(JsonNode nodes) {
        StringBuilder out = new StringBuilder();
        for (JsonNode node : nodes) {
            String type = node.path("type").asText("");
            if ("text".equals(type)) {
                out.append(htmlText(node.path("text").asText(""), node.path("marks")));
            } else if ("hardBreak".equals(type)) {
                out.append("<br/>");
            } else if ("image".equals(type)) {
                out.append("<img src=\"").append(escape(safeImageSrc(node.path("attrs").path("src").asText(""))))
                        .append("\" alt=\"").append(escape(node.path("attrs").path("alt").asText(""))).append("\"/>");
            } else if (node.path("content").isArray()) {
                out.append(htmlInline(node.path("content")));
            }
        }
        return out.toString();
    }

    /**
     * 为文本套用 HTML 行内标记。
     *
     * @param text  文本
     * @param marks 标记数组
     * @return HTML 文本
     */
    private String htmlText(String text, JsonNode marks) {
        String result = escape(text);
        for (JsonNode mark : marks) {
            switch (mark.path("type").asText("")) {
                case "bold" -> result = "<strong>" + result + "</strong>";
                case "italic" -> result = "<em>" + result + "</em>";
                case "code" -> result = "<code>" + result + "</code>";
                case "strike" -> result = "<s>" + result + "</s>";
                case "link" -> {
                    String href = safeLinkHref(mark.path("attrs").path("href").asText(""));
                    if (!href.isEmpty()) {
                        result = "<a href=\"" + escape(href) + "\" rel=\"noopener noreferrer\">" + result + "</a>";
                    }
                }
                default -> {
                }
            }
        }
        return result;
    }

    /**
     * 读取 Markdown 代码块。
     *
     * @param lines 文本行
     * @param index 起始行（三个反引号）
     * @param blocks 块列表
     * @return 下一个待处理行下标
     */
    private int readCodeBlock(List<String> lines, int index, List<Object> blocks) {
        String opening = lines.get(index).trim().substring(3).trim();
        StringBuilder code = new StringBuilder();
        int cursor = index + 1;
        while (cursor < lines.size() && !lines.get(cursor).trim().startsWith("```")) {
            if (!code.isEmpty()) {
                code.append('\n');
            }
            code.append(lines.get(cursor));
            cursor++;
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (!opening.isEmpty()) {
            attrs.put("language", opening);
        }
        blocks.add(block("codeBlock", attrs, textNode(code.toString())));
        return Math.min(cursor + 1, lines.size());
    }

    /**
     * 读取 Markdown 引用块。
     *
     * @param lines  文本行
     * @param index  起始行
     * @param blocks 块列表
     * @return 下一个待处理行下标
     */
    private int readQuote(List<String> lines, int index, List<Object> blocks) {
        StringBuilder text = new StringBuilder();
        int cursor = index;
        while (cursor < lines.size() && lines.get(cursor).trim().startsWith(">")) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(lines.get(cursor).trim().substring(1).trim());
            cursor++;
        }
        List<Object> content = List.of(block("paragraph", Map.of(), inline(text.toString())));
        blocks.add(block("blockquote", Map.of(), content));
        return cursor;
    }

    /**
     * 读取 Markdown 列表块。
     *
     * @param lines   文本行
     * @param index   起始行
     * @param blocks  块列表
     * @param ordered 是否有序列表
     * @return 下一个待处理行下标
     */
    private int readList(List<String> lines, int index, List<Object> blocks, boolean ordered) {
        List<Object> items = new ArrayList<>();
        int cursor = index;
        Pattern pattern = ordered ? MD_ORDERED : MD_BULLET;
        while (cursor < lines.size()) {
            String line = lines.get(cursor);
            Matcher matcher = pattern.matcher(line);
            if (!matcher.matches()) {
                break;
            }
            items.add(block("listItem", Map.of(), List.of(
                    block("paragraph", Map.of(), inline(matcher.group(1).trim())))));
            cursor++;
        }
        blocks.add(block(ordered ? "orderedList" : "bulletList", Map.of(), items));
        return cursor;
    }

    /**
     * 读取 Markdown 表格块。
     *
     * @param lines  文本行
     * @param index  起始行
     * @param blocks 块列表
     * @return 下一个待处理行下标
     */
    private int readTable(List<String> lines, int index, List<Object> blocks) {
        List<List<String>> cells = new ArrayList<>();
        int cursor = index;
        while (cursor < lines.size() && lines.get(cursor).trim().startsWith("|")) {
            String line = lines.get(cursor).trim();
            if (!MD_RULE.matcher(line).matches()) {
                List<String> row = new ArrayList<>();
                for (String part : line.substring(1, Math.max(1, line.length() - 1)).split("\\|", -1)) {
                    row.add(part.trim());
                }
                cells.add(row);
            }
            cursor++;
        }
        List<Object> rows = new ArrayList<>();
        for (List<String> row : cells) {
            List<Object> columns = new ArrayList<>();
            for (String value : row) {
                columns.add(block("tableCell", Map.of(), List.of(
                        block("paragraph", Map.of(), inline(value)))));
            }
            rows.add(block("tableRow", Map.of(), columns));
        }
        blocks.add(block("table", Map.of(), rows));
        return cursor;
    }

    /**
     * 读取 Markdown 普通段落。
     *
     * @param lines  文本行
     * @param index  起始行
     * @param blocks 块列表
     * @return 下一个待处理行下标
     */
    private int readParagraph(List<String> lines, int index, List<Object> blocks) {
        StringBuilder text = new StringBuilder();
        int cursor = index;
        while (cursor < lines.size()) {
            String line = lines.get(cursor);
            if (line.isBlank() || isBlockStart(line)) {
                break;
            }
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(line.trim());
            cursor++;
        }
        if (!text.isEmpty()) {
            blocks.add(block("paragraph", Map.of(), inline(text.toString())));
        }
        return cursor;
    }

    /**
     * 判断一行是否开启新的 Markdown 块。
     *
     * @param line 文本行
     * @return 新块起始返回 {@code true}
     */
    private boolean isBlockStart(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("```") || trimmed.startsWith(">") || trimmed.startsWith("|")
                || MD_HEADING.matcher(trimmed).matches() || MD_RULE.matcher(line).matches()
                || MD_BULLET.matcher(line).matches() || MD_ORDERED.matcher(line).matches();
    }

    /**
     * 解析 Markdown 行内文本为 TipTap 文本节点列表。
     *
     * @param text 行内文本
     * @return 节点列表
     */
    private List<Object> inline(String text) {
        List<Object> nodes = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return nodes;
        }
        Matcher matcher = INLINE.matcher(text);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                nodes.add(textNode(text.substring(last, matcher.start())));
            }
            nodes.add(markedNode(matcher));
            last = matcher.end();
        }
        if (last < text.length()) {
            nodes.add(textNode(text.substring(last)));
        }
        return nodes.isEmpty() ? List.of(textNode(text)) : nodes;
    }

    /**
     * 将行内标记匹配结果转为带 marks 的文本节点。
     *
     * @param matcher 正则匹配
     * @return 文本节点
     */
    private Object markedNode(Matcher matcher) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "text");
        node.put("text", matcher.group(1) != null ? matcher.group(1)
                : matcher.group(2) != null ? matcher.group(2)
                : matcher.group(3) != null ? matcher.group(3)
                : matcher.group(4) != null ? matcher.group(4) : matcher.group(5));
        Map<String, Object> mark = new LinkedHashMap<>();
        if (matcher.group(1) != null) {
            mark.put("type", "bold");
        } else if (matcher.group(2) != null) {
            mark.put("type", "italic");
        } else if (matcher.group(3) != null) {
            mark.put("type", "code");
        } else if (matcher.group(4) != null) {
            mark.put("type", "strike");
        } else {
            String href = safeLinkHref(matcher.group(6));
            if (href.isEmpty()) {
                return node;
            }
            mark.put("type", "link");
            mark.put("attrs", Map.of("href", href, "target", "_blank"));
        }
        node.put("marks", List.of(mark));
        return node;
    }

    /**
     * 构造纯文本节点。
     *
     * @param text 文本
     * @return 文本节点
     */
    private Object textNode(String text) {
        return Map.of("type", "text", "text", text);
    }

    /**
     * 构造块节点。
     *
     * @param type    节点类型
     * @param attrs   属性
     * @param content 子节点
     * @return 块节点
     */
    @SuppressWarnings("unchecked")
    private Object block(String type, Map<String, Object> attrs, Object content) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        if (!attrs.isEmpty()) {
            node.put("attrs", attrs);
        }
        if (content != null) {
            node.put("content", content instanceof List<?> list ? list
                    : List.of(content));
        }
        return node;
    }

    /**
     * 将块列表序列化为 TipTap 文档 JSON。
     *
     * @param blocks 块列表
     * @return JSON 字符串
     */
    private String stringify(List<Object> blocks) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", TYPE_DOC);
        doc.put("content", blocks);
        try {
            return objectMapper.writeValueAsString(doc);
        } catch (RuntimeException ex) {
            throw DocErrorCode.exception(DocErrorCode.IMPORT_FILE_INVALID);
        }
    }

    /**
     * 将 HTML 文本块关闭为 TipTap 块列表。
     *
     * @param tag   块标签
     * @param text  块内纯文本
     * @return 块列表
     */
    private List<Object> closeHtmlBlock(String tag, String text) {
        String value = text.trim();
        if (tag.matches("h[1-6]")) {
            return List.of(block("heading", Map.of("level", Integer.parseInt(tag.substring(1))),
                    inline(value)));
        }
        if ("ul".equals(tag) || "ol".equals(tag)) {
            return List.of();
        }
        if ("pre".equals(tag)) {
            return List.of(block("codeBlock", Map.of(), text.stripLeading()));
        }
        if ("li".equals(tag)) {
            return List.of(block("listItem", Map.of(), List.of(
                    block("paragraph", Map.of(), inline(value)))));
        }
        if ("blockquote".equals(tag)) {
            return List.of(block("blockquote", Map.of(), List.of(
                    block("paragraph", Map.of(), inline(value)))));
        }
        return value.isEmpty() ? List.of() : List.of(block("paragraph", Map.of(), inline(value)));
    }

    /**
     * 追加纯文本为段落。
     *
     * @param blocks 块列表
     * @param text   纯文本
     */
    private void flushText(List<Object> blocks, String text) {
        String value = text.trim();
        if (!value.isEmpty()) {
            blocks.add(block("paragraph", Map.of(), inline(value)));
        }
    }

    /**
     * 去除 HTML 标签并反转义常见实体。
     *
     * @param html HTML 片段
     * @return 纯文本
     */
    private String stripInline(String html) {
        return html.replaceAll("(?is)<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }

    /**
     * HTML 转义。
     *
     * @param text 原始文本
     * @return 转义文本
     */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * 过滤链接协议，阻断 {@code javascript:} / {@code data:} / {@code vbscript:} 等可执行协议。
     *
     * <p>站内相对路径、锚点以及 {@code http/https/mailto} 原样保留；未知协议返回空串，
     * 导出侧降级为纯文本。</p>
     *
     * @param href 原始链接
     * @return 可安全导出的链接，非法时为空串
     */
    private String safeLinkHref(String href) {
        String value = href == null ? "" : href.trim();
        if (value.isEmpty() || value.startsWith("/") || value.startsWith("#")) {
            return value;
        }
        Matcher matcher = SCHEME.matcher(value);
        if (!matcher.find()) {
            return value;
        }
        String scheme = matcher.group(1).toLowerCase(Locale.ROOT);
        return switch (scheme) {
            case "http", "https", "mailto" -> value;
            default -> "";
        };
    }

    /**
     * 过滤图片地址协议，仅允许 {@code http/https} 与站内相对路径。
     *
     * @param src 原始图片地址
     * @return 可安全导出的地址，非法时为空串
     */
    private String safeImageSrc(String src) {
        String value = src == null ? "" : src.trim();
        if (value.isEmpty() || value.startsWith("/")) {
            return value;
        }
        Matcher matcher = SCHEME.matcher(value);
        if (!matcher.find()) {
            return value;
        }
        String scheme = matcher.group(1).toLowerCase(Locale.ROOT);
        return switch (scheme) {
            case "http", "https" -> value;
            default -> "";
        };
    }

    /**
     * 归一化 Markdown 空白。
     *
     * @param text 原始文本
     * @return 归一化文本
     */
    private String normalize(String text) {
        return text.replaceAll("\\n{3,}", "\n\n").trim();
    }
}
