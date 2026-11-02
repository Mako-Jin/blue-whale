package com.bluewhale.doc.support;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DocMarkupSupport} 导出/导入行为断言。
 *
 * @author BlueWhale
 */
class DocMarkupSupportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private final DocMarkupSupport support = new DocMarkupSupport(mapper);

    // ---------------------------------------------------------------- 导出 Markdown

    @Test
    void markdownExportShouldReturnEmptyForBlankInput() {
        assertThat(support.toMarkdown(null)).isEmpty();
        assertThat(support.toMarkdown("")).isEmpty();
        assertThat(support.toMarkdown("   ")).isEmpty();
    }

    @Test
    void markdownExportShouldRejectInvalidJson() {
        assertThatThrownBy(() -> support.toMarkdown("{not-json"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("正文 JSON 无法解析");
    }

    @Test
    void markdownExportShouldIgnoreNonObjectRoot() {
        assertThat(support.toMarkdown("[1,2]")).isEmpty();
        assertThat(support.toMarkdown("\"text\"")).isEmpty();
    }

    @Test
    void markdownExportShouldRenderHeadingAndParagraph() throws Exception {
        String markdown = support.toMarkdown(doc(
                "{\"type\":\"heading\",\"attrs\":{\"level\":3},\"content\":[" + text("Title") + "]}",
                paragraph(text("body"))));

        assertThat(markdown).contains("### Title").contains("body");
    }

    @Test
    void markdownExportShouldDefaultHeadingLevelToOne() throws Exception {
        String markdown = support.toMarkdown(doc(
                "{\"type\":\"heading\",\"content\":[" + text("H") + "]}"));

        assertThat(markdown).startsWith("# H");
    }

    @Test
    void markdownExportShouldSkipBlankParagraph() throws Exception {
        String markdown = support.toMarkdown(doc(paragraph(text("   "))));

        assertThat(markdown).isEmpty();
    }

    @Test
    void markdownExportShouldRenderCodeBlockWithLanguage() throws Exception {
        String markdown = support.toMarkdown(doc(
                "{\"type\":\"codeBlock\",\"attrs\":{\"language\":\"java\"},\"content\":["
                        + text("int a;") + "]}"));

        assertThat(markdown).isEqualTo("```java\nint a;\n```");
    }

    @Test
    void markdownExportShouldRenderBlockquoteLines() throws Exception {
        String markdown = support.toMarkdown(doc(
                "{\"type\":\"blockquote\",\"content\":[" + paragraph(text("line1"), hardBreak(), text("line2")) + "]}"));

        assertThat(markdown).isEqualTo("> line1\n> line2");
    }

    @Test
    void markdownExportShouldRenderHorizontalRule() throws Exception {
        String markdown = support.toMarkdown(doc("{\"type\":\"horizontalRule\"}"));

        assertThat(markdown).isEqualTo("---");
    }

    @Test
    void markdownExportShouldRenderLists() throws Exception {
        String markdown = support.toMarkdown(doc(
                "{\"type\":\"bulletList\",\"content\":["
                        + "{\"type\":\"listItem\",\"content\":[" + paragraph(text("one")) + "]},"
                        + "{\"type\":\"listItem\",\"content\":[" + paragraph(text("two")) + "]}]}"));

        assertThat(markdown).startsWith("- one\n").contains("- two").contains("one").contains("two");
    }

    @Test
    void markdownExportShouldRenderOrderedList() throws Exception {
        String markdown = support.toMarkdown(doc(
                "{\"type\":\"orderedList\",\"content\":["
                        + "{\"type\":\"listItem\",\"content\":[" + paragraph(text("one")) + "]},"
                        + "{\"type\":\"listItem\",\"content\":[" + paragraph(text("two")) + "]}]}"));

        assertThat(markdown).startsWith("1. one\n").contains("2. two");
    }

    @Test
    void markdownExportShouldRenderTable() throws Exception {
        String markdown = support.toMarkdown(doc(
                "{\"type\":\"table\",\"content\":["
                        + row(text("a"), text("b")) + "," + row(text("c"), text("d")) + "]}"));

        assertThat(markdown).contains("| a | b |");
        assertThat(markdown).contains("| --- | --- |");
        assertThat(markdown).contains("| c | d |");
    }

    @Test
    void markdownExportShouldRenderImageBlockAndKeepSafeSource() throws Exception {
        String markdown = support.toMarkdown(doc(
                "{\"type\":\"image\",\"attrs\":{\"src\":\"https://example.com/a.png\",\"alt\":\"pic\"}}"));

        assertThat(markdown).isEqualTo("![pic](https://example.com/a.png)");
    }

    @Test
    void markdownExportShouldDropExecutableImageSource() throws Exception {
        String markdown = support.toMarkdown(doc(
                "{\"type\":\"image\",\"attrs\":{\"src\":\"javascript:alert(1)\",\"alt\":\"pic\"}}"));

        assertThat(markdown).isEqualTo("![pic]()");
    }

    @Test
    void markdownExportShouldRecurseUnknownNodes() throws Exception {
        String markdown = support.toMarkdown(doc(
                "{\"type\":\"wrapper\",\"content\":[" + paragraph(text("inside")) + "]}"));

        assertThat(markdown).isEqualTo("inside");
    }

    @Test
    void markdownExportShouldApplyInlineMarks() throws Exception {
        String marked = support.toMarkdown(doc(paragraph(
                marked("bold", "bold", null), marked("italic", "italic", null),
                marked("code", "code", null), marked("strike", "strike", null))));

        assertThat(marked).contains("**bold**").contains("*italic*")
                .contains("`code`").contains("~~strike~~");
    }

    @Test
    void markdownExportShouldRenderLinksAndDropUnsafeProtocols() throws Exception {
        String markdown = support.toMarkdown(doc(paragraph(
                link("site", "https://example.com"),
                link("mail", "mailto:a@b.com"),
                link("rel", "/docs/1"),
                link("hash", "#top"),
                link("plain", "docs/1"),
                link("bad", "javascript:alert(1)"),
                link("blank", ""))));

        assertThat(markdown).contains("[site](https://example.com)")
                .contains("[mail](mailto:a@b.com)")
                .contains("[rel](/docs/1)")
                .contains("[hash](#top)")
                .contains("[plain](docs/1)")
                .contains("bad")
                .doesNotContain("javascript:");
    }

    @Test
    void markdownExportShouldIgnoreUnknownMarks() throws Exception {
        String markdown = support.toMarkdown(doc(paragraph(
                "{\"type\":\"text\",\"text\":\"plain\",\"marks\":[{\"type\":\"underline\"}]}")));

        assertThat(markdown).isEqualTo("plain");
    }

    @Test
    void markdownExportShouldRenderInlineImageWithoutAltFallback() throws Exception {
        String markdown = support.toMarkdown(doc(paragraph(
                "{\"type\":\"image\",\"attrs\":{\"src\":\"/img/a.png\"}}" )));

        assertThat(markdown).isEqualTo("![](/img/a.png)");
    }

    @Test
    void markdownExportShouldNormalizeRepeatedBlankLines() throws Exception {
        String markdown = support.toMarkdown(doc(paragraph(text("a")), paragraph(text("b"))));

        assertThat(markdown).isEqualTo("a\n\nb");
    }

    // ---------------------------------------------------------------- 导出 HTML

    @Test
    void htmlExportShouldReturnEmptyForBlankInput() {
        assertThat(support.toHtml(null)).isEmpty();
        assertThat(support.toHtml(" ")).isEmpty();
    }

    @Test
    void htmlExportShouldEscapeScriptText() {
        String html = support.toHtml(content("<script>alert('xss')</script>"));
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void htmlExportShouldRenderCommonBlocks() throws Exception {
        String html = support.toHtml(doc(
                "{\"type\":\"heading\",\"attrs\":{\"level\":2},\"content\":[" + text("T") + "]}",
                paragraph(text("p")),
                "{\"type\":\"codeBlock\",\"content\":[" + text("a<b") + "]}",
                "{\"type\":\"blockquote\",\"content\":[" + paragraph(text("q")) + "]}",
                "{\"type\":\"horizontalRule\"}"));

        assertThat(html).contains("<h2>T</h2>").contains("<p>p</p>")
                .contains("<pre><code>a&lt;b</code></pre>")
                .contains("<blockquote>q</blockquote>")
                .contains("<hr/>");
    }

    @Test
    void htmlExportShouldRenderNestedLists() throws Exception {
        String html = support.toHtml(doc(
                "{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"content\":["
                        + paragraph(text("one")) + ","
                        + "{\"type\":\"orderedList\",\"content\":[{\"type\":\"listItem\",\"content\":["
                        + paragraph(text("nested")) + "]}]}]}]}"));

        assertThat(html).contains("<ul>").contains("<li>").contains("<ol>")
                .contains("nested").contains("</ol>").contains("</ul>");
    }

    @Test
    void htmlExportShouldRenderTableWithCells() throws Exception {
        String html = support.toHtml(doc(
                "{\"type\":\"table\",\"content\":[" + row(text("a"), text("b")) + "]}"));

        assertThat(html).isEqualTo("<table><tr><td>a</td><td>b</td></tr></table>\n");
    }

    @Test
    void htmlExportShouldRenderImageAndDropUnsafeSource() throws Exception {
        String html = support.toHtml(doc(
                "{\"type\":\"image\",\"attrs\":{\"src\":\"https://example.com/a.png\",\"alt\":\"pi<c\"}}",
                "{\"type\":\"image\",\"attrs\":{\"src\":\"data:image/png;base64,xx\",\"alt\":\"bad\"}}" ));

        assertThat(html).contains("<img src=\"https://example.com/a.png\" alt=\"pi&lt;c\"/>")
                .contains("<img src=\"\" alt=\"bad\"/>");
    }

    @Test
    void htmlExportShouldRenderInlineMarksAndHardBreak() throws Exception {
        String html = support.toHtml(doc(paragraph(
                marked("bold", "b", null), marked("italic", "i", null),
                marked("code", "c", null), marked("strike", "s", null),
                hardBreak(), link("l", "https://example.com"))));

        assertThat(html).contains("<strong>b</strong>").contains("<em>i</em>")
                .contains("<code>c</code>").contains("<s>s</s>")
                .contains("<a href=\"https://example.com\" rel=\"noopener noreferrer\">l</a>")
                .contains("<br/>");
    }

    @Test
    void htmlExportShouldRecurseUnknownNodes() throws Exception {
        String html = support.toHtml(doc("{\"type\":\"wrapper\",\"content\":[" + paragraph(text("x")) + "]}"));

        assertThat(html).isEqualTo("<p>x</p>\n");
    }

    @Test
    void htmlExportShouldEscapeTextSpecialCharacters() throws Exception {
        String html = support.toHtml(doc(paragraph(text("a&b<c>\"d'"))));

        assertThat(html).isEqualTo("<p>a&amp;b&lt;c&gt;&quot;d&#39;</p>\n");
    }

    @Test
    void htmlExportShouldRenderOrderedList() throws Exception {
        String html = support.toHtml(doc(
                "{\"type\":\"orderedList\",\"content\":[{\"type\":\"listItem\",\"content\":["
                        + paragraph(text("one")) + "]}]}"));

        assertThat(html).isEqualTo("<ol><li>one</li></ol>\n");
    }

    @Test
    void htmlExportShouldTagNestedBulletList() throws Exception {
        String html = support.toHtml(doc(
                "{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"content\":["
                        + paragraph(text("outer")) + ",{\"type\":\"bulletList\",\"content\":["
                        + "{\"type\":\"listItem\",\"content\":[" + paragraph(text("inner")) + "]}]}]}]}"));

        assertThat(html).contains("<ul>", "<li>inner</li>", "</li></ul></li></ul>")
                .doesNotContain("<ol>");
    }

    @Test
    void htmlExportShouldRenderInlineImage() throws Exception {
        String html = support.toHtml(doc(paragraph(
                "{\"type\":\"image\",\"attrs\":{\"src\":\"/img/a.png\",\"alt\":\"x\"}}")));

        assertThat(html).isEqualTo("<p><img src=\"/img/a.png\" alt=\"x\"/></p>\n");
    }

    // ---------------------------------------------------------------- 导入 Markdown

    @Test
    void importMarkdownShouldReturnEmptyDocumentForBlankInput() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown(""));

        assertThat(doc.path("type").asText()).isEqualTo("doc");
        assertThat(doc.path("content").size()).isZero();
    }

    @Test
    void importMarkdownShouldParseHeadings() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("# One\n## Two\n###### Six"));

        assertThat(doc.path("content").size()).isEqualTo(3);
        assertThat(doc.path("content").get(0).path("attrs").path("level").asInt()).isEqualTo(1);
        assertThat(doc.path("content").get(0).path("content").get(0).path("text").asText()).isEqualTo("One");
        assertThat(doc.path("content").get(1).path("attrs").path("level").asInt()).isEqualTo(2);
        assertThat(doc.path("content").get(2).path("attrs").path("level").asInt()).isEqualTo(6);
    }

    @Test
    void importMarkdownShouldTreatSevenHashesAsParagraph() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("####### Seven"));

        assertThat(doc.path("content").get(0).path("type").asText()).isEqualTo("paragraph");
        assertThat(doc.path("content").get(0).path("content").get(0).path("text").asText()).isEqualTo("####### Seven");
    }

    @Test
    void importMarkdownShouldParseHorizontalRules() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("---\n***\n___"));

        assertThat(doc.path("content").size()).isEqualTo(3);
        doc.path("content").forEach(node ->
                assertThat(node.path("type").asText()).isEqualTo("horizontalRule"));
    }

    @Test
    void importMarkdownShouldParseCodeBlockWithLanguage() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("```java\nint a;\n```"));

        JsonNode block = doc.path("content").get(0);
        assertThat(block.path("type").asText()).isEqualTo("codeBlock");
        assertThat(block.path("attrs").path("language").asText()).isEqualTo("java");
        assertThat(block.path("content").get(0).path("text").asText()).isEqualTo("int a;");
    }

    @Test
    void importMarkdownShouldParseUnterminatedCodeBlock() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("```\nx\ny"));

        JsonNode block = doc.path("content").get(0);
        assertThat(block.path("type").asText()).isEqualTo("codeBlock");
        assertThat(block.path("content").get(0).path("text").asText()).isEqualTo("x\ny");
    }

    @Test
    void importMarkdownShouldParseQuoteBlock() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("> first\n> second"));

        JsonNode quote = doc.path("content").get(0);
        assertThat(quote.path("type").asText()).isEqualTo("blockquote");
        assertThat(quote.path("content").get(0).path("content").get(0).path("text").asText())
                .isEqualTo("first\nsecond");
    }

    @Test
    void importMarkdownShouldParseBulletAndOrderedLists() throws Exception {
        JsonNode bullets = mapper.readTree(support.fromMarkdown("- a\n* b\n+ c"));
        JsonNode ordered = mapper.readTree(support.fromMarkdown("1. a\n2. b"));

        assertThat(bullets.path("content").get(0).path("type").asText()).isEqualTo("bulletList");
        assertThat(bullets.path("content").get(0).path("content").size()).isEqualTo(3);
        assertThat(ordered.path("content").get(0).path("type").asText()).isEqualTo("orderedList");
        assertThat(ordered.path("content").get(0).path("content").get(1)
                .path("content").get(0).path("content").get(0).path("text").asText()).isEqualTo("b");
    }

    @Test
    void importMarkdownShouldStopListAtBlockBoundary() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("- a\n# H"));

        assertThat(doc.path("content").size()).isEqualTo(2);
        assertThat(doc.path("content").get(0).path("type").asText()).isEqualTo("bulletList");
        assertThat(doc.path("content").get(0).path("content").size()).isEqualTo(1);
        assertThat(doc.path("content").get(1).path("type").asText()).isEqualTo("heading");
    }

    @Test
    void importMarkdownShouldParseTableRows() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("| a | b |\n| --- | --- |\n| c | d |"));

        JsonNode table = doc.path("content").get(0);
        assertThat(table.path("type").asText()).isEqualTo("table");
        assertThat(table.path("content").size()).isEqualTo(3);
        assertThat(table.path("content").get(0).path("content").size()).isEqualTo(2);
        assertThat(table.path("content").get(2).path("content").get(0)
                .path("content").get(0).path("content").get(0).path("text").asText()).isEqualTo("c");
    }

    @Test
    void importMarkdownShouldJoinParagraphLines() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("line one\nline two\n\nnext"));

        assertThat(doc.path("content").size()).isEqualTo(2);
        assertThat(doc.path("content").get(0).path("content").get(0).path("text").asText())
                .isEqualTo("line one line two");
    }

    @Test
    void importMarkdownShouldStopParagraphAtBlockStart() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("text\n# Heading"));

        assertThat(doc.path("content").size()).isEqualTo(2);
        assertThat(doc.path("content").get(1).path("type").asText()).isEqualTo("heading");
    }

    @Test
    void importMarkdownShouldParseInlineMarks() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown(
                "**b** *i* `c` ~~s~~ [l](https://example.com)"));

        JsonNode nodes = doc.path("content").get(0).path("content");
        assertThat(nodes.size()).isEqualTo(9);
        assertThat(nodes.get(0).path("marks").get(0).path("type").asText()).isEqualTo("bold");
        assertThat(nodes.get(2).path("marks").get(0).path("type").asText()).isEqualTo("italic");
        assertThat(nodes.get(4).path("marks").get(0).path("type").asText()).isEqualTo("code");
        assertThat(nodes.get(6).path("marks").get(0).path("type").asText()).isEqualTo("strike");
        assertThat(nodes.get(8).path("marks").get(0).path("attrs").path("href").asText())
                .isEqualTo("https://example.com");
        assertThat(nodes.get(8).path("marks").get(0).path("attrs").path("target").asText())
                .isEqualTo("_blank");
    }

    @Test
    void importMarkdownShouldKeepSurroundingTextAroundMarks() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("a **b** c"));

        JsonNode nodes = doc.path("content").get(0).path("content");
        assertThat(nodes.size()).isEqualTo(3);
        assertThat(nodes.get(0).path("text").asText()).isEqualTo("a ");
        assertThat(nodes.get(1).path("text").asText()).isEqualTo("b");
        assertThat(nodes.get(2).path("text").asText()).isEqualTo(" c");
    }

    @Test
    void importMarkdownShouldDowngradeUnsafeLinksToText() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("[x](javascript:alert(1))"));

        JsonNode node = doc.path("content").get(0).path("content").get(0);
        assertThat(node.path("text").asText()).isEqualTo("x");
        assertThat(node.path("marks").isMissingNode()).isTrue();
    }

    @Test
    void importMarkdownShouldKeepPlainTextWithoutMarks() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("plain text without markup"));

        assertThat(doc.path("content").get(0).path("content").get(0).path("text").asText())
                .isEqualTo("plain text without markup");
    }

    @Test
    void importMarkdownShouldHandleHeadingWithoutInlineContent() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("#"));

        // 无空白分隔，按普通段落处理
        assertThat(doc.path("content").get(0).path("type").asText()).isEqualTo("paragraph");
    }

    @Test
    void importMarkdownShouldNotCrashOnOnlyBlankLines() throws Exception {
        JsonNode doc = mapper.readTree(support.fromMarkdown("\n\n\n"));

        assertThat(doc.path("content").size()).isZero();
    }

    // ---------------------------------------------------------------- 导入 HTML

    @Test
    void importHtmlShouldReturnEmptyDocumentForBlankInput() throws Exception {
        JsonNode doc = mapper.readTree(support.fromHtml(""));

        assertThat(doc.path("content").size()).isZero();
    }

    @Test
    void importHtmlShouldRemoveScriptAndStyle() throws Exception {
        JsonNode doc = mapper.readTree(support.fromHtml(
                "<script>alert(1)</script><style>.a{}</style><p>hello</p>"));

        assertThat(doc.path("content").size()).isEqualTo(1);
        assertThat(doc.path("content").get(0).path("content").get(0).path("text").asText())
                .isEqualTo("hello");
    }

    @Test
    void importHtmlShouldParseHeading() throws Exception {
        JsonNode doc = mapper.readTree(support.fromHtml("<h3>Title</h3>"));

        JsonNode block = doc.path("content").get(0);
        assertThat(block.path("type").asText()).isEqualTo("heading");
        assertThat(block.path("attrs").path("level").asInt()).isEqualTo(3);
    }

    @Test
    void importHtmlShouldSplitParagraphOnBreak() throws Exception {
        JsonNode doc = mapper.readTree(support.fromHtml("<p>a<br>b</p>"));

        assertThat(doc.path("content").size()).isEqualTo(2);
        assertThat(doc.path("content").get(0).path("content").get(0).path("text").asText()).isEqualTo("a");
        assertThat(doc.path("content").get(1).path("content").get(0).path("text").asText()).isEqualTo("b");
    }

    @Test
    void importHtmlShouldParseHorizontalRule() throws Exception {
        JsonNode doc = mapper.readTree(support.fromHtml("<hr>"));

        assertThat(doc.path("content").get(0).path("type").asText()).isEqualTo("horizontalRule");
    }

    @Test
    void importHtmlShouldParseListItemAndBlockquote() throws Exception {
        JsonNode list = mapper.readTree(support.fromHtml("<li>a</li>"));
        JsonNode quote = mapper.readTree(support.fromHtml("<blockquote>q</blockquote>"));

        assertThat(list.path("content").get(0).path("type").asText()).isEqualTo("listItem");
        assertThat(quote.path("content").get(0).path("type").asText()).isEqualTo("blockquote");
        assertThat(quote.path("content").get(0).path("content").get(0)
                .path("content").get(0).path("text").asText()).isEqualTo("q");
    }

    @Test
    void importHtmlShouldParsePreformattedBlock() throws Exception {
        JsonNode doc = mapper.readTree(support.fromHtml("<pre>  code line  </pre>"));

        JsonNode block = doc.path("content").get(0);
        assertThat(block.path("type").asText()).isEqualTo("codeBlock");
        assertThat(block.path("content").get(0).asText()).isEqualTo("code line  ");
    }

    @Test
    void importHtmlShouldDropListWrapperBlocks() throws Exception {
        JsonNode doc = mapper.readTree(support.fromHtml("<ul><li>a</li></ul>"));

        assertThat(doc.path("content").size()).isZero();
    }

    @Test
    void importHtmlShouldStripUnknownTagsAndDecodeEntities() throws Exception {
        JsonNode doc = mapper.readTree(support.fromHtml(
                "<div><p>a&nbsp;&amp;&lt;b&gt;&quot;c&quot;&#39;</p></div>"));

        assertThat(doc.path("content").get(0).path("content").get(0).path("text").asText())
                .isEqualTo("a &<b>\"c\"'");
    }

    @Test
    void importHtmlShouldFlushPlainTextWithoutTags() throws Exception {
        JsonNode doc = mapper.readTree(support.fromHtml("just text"));

        assertThat(doc.path("content").get(0).path("type").asText()).isEqualTo("paragraph");
    }

    @Test
    void importHtmlShouldFlushMismatchedClosingTagAsParagraph() throws Exception {
        JsonNode doc = mapper.readTree(support.fromHtml("<h1>x</h2>"));

        assertThat(doc.path("content").size()).isEqualTo(1);
        assertThat(doc.path("content").get(0).path("type").asText()).isEqualTo("paragraph");
    }

    @Test
    void importHtmlShouldKeepNestedInlineText() throws Exception {
        JsonNode doc = mapper.readTree(support.fromHtml("<p>a <strong>b</strong></p>"));

        assertThat(doc.path("content").get(0).path("content").get(0).path("text").asText())
                .isEqualTo("a b");
    }

    @Test
    void importHtmlShouldSkipEmptyParagraphOnClosing() throws Exception {
        JsonNode doc = mapper.readTree(support.fromHtml("<p></p>"));

        assertThat(doc.path("content").size()).isZero();
    }

    @Test
    void markdownExportShouldIgnoreNonArrayRootContent() {
        assertThat(support.toMarkdown("{\"type\":\"doc\",\"content\":{}}")).isEmpty();
    }

    @Test
    void markdownExportShouldRenderListItemAtRoot() throws Exception {
        String markdown = support.toMarkdown(doc(
                "{\"type\":\"listItem\",\"content\":[" + paragraph(text("item")) + "]}"));

        assertThat(markdown).isEqualTo("item");
    }

    @Test
    void markdownExportShouldKeepUnschemedRelativeImageSource() throws Exception {
        String markdown = support.toMarkdown(doc(
                "{\"type\":\"image\",\"attrs\":{\"src\":\"img/a.png\"}}"));

        assertThat(markdown).isEqualTo("![](img/a.png)");
    }

    // ---------------------------------------------------------------- 构造工具

    /**
     * 构造文档 JSON。
     *
     * @param blocks 块节点 JSON
     * @return 文档 JSON
     */
    private String doc(String... blocks) {
        return "{\"type\":\"doc\",\"content\":[" + String.join(",", blocks) + "]}";
    }

    /**
     * 构造段落块。
     *
     * @param nodes 行内节点 JSON
     * @return 段落 JSON
     */
    private String paragraph(String... nodes) {
        return "{\"type\":\"paragraph\",\"content\":[" + String.join(",", nodes) + "]}";
    }

    /**
     * 构造普通文本节点。
     *
     * @param text 文本
     * @return 文本节点 JSON
     */
    private String text(String text) {
        return "{\"type\":\"text\",\"text\":\"" + text.replace("\"", "\\\"").replace("<", "\\u003c")
                .replace(">", "\\u003e") + "\"}";
    }

    /**
     * 构造换行节点。
     *
     * @return hardBreak JSON
     */
    private String hardBreak() {
        return "{\"type\":\"hardBreak\"}";
    }

    /**
     * 构造带单个 mark 的文本节点。
     *
     * @param mark 标记类型
     * @param text 文本
     * @param href 链接地址，非链接标记时忽略
     * @return 文本节点 JSON
     */
    private String marked(String mark, String text, String href) {
        String attrs = href == null ? "" : ",\"attrs\":{\"href\":\"" + href + "\"}";
        return "{\"type\":\"text\",\"text\":\"" + text + "\",\"marks\":[{\"type\":\""
                + mark + "\"" + attrs + "}]}";
    }

    /**
     * 构造链接文本节点。
     *
     * @param text 文本
     * @param href 链接
     * @return 文本节点 JSON
     */
    private String link(String text, String href) {
        return marked("link", text, href);
    }

    /**
     * 构造表格行。
     *
     * @param cells 单元格文本节点
     * @return 行 JSON
     */
    private String row(String... cells) {
        StringBuilder row = new StringBuilder("{\"type\":\"tableRow\",\"content\":[");
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append("{\"type\":\"tableCell\",\"content\":[").append(cells[i]).append("]}");
        }
        return row.append("]}").toString();
    }

    /**
     * 构造带单个文本节点的 TipTap 正文（历史用例兼容）。
     *
     * @param text 文本
     * @return 正文 JSON
     */
    private String content(String text) {
        return content(text, null, null);
    }

    /**
     * 构造可附带 mark 的 TipTap 正文（历史用例兼容）。
     *
     * @param text 文本
     * @param mark 标记类型
     * @param href 链接地址
     * @return 正文 JSON
     */
    private String content(String text, String mark, String href) {
        return doc(paragraph(mark == null ? text(text) : marked(mark, text, href)));
    }
}
