package com.bluewhale.agent.support.knowledge;

import com.bluewhale.agent.config.AgentProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档解析器测试：六类业务文档可抽取，损坏/不支持/超大文件被拒绝。
 *
 * @author BlueWhale
 */
class FileDocumentParserTest {

    private final AgentProperties properties = new AgentProperties();

    private final FileDocumentParser parser = new FileDocumentParser(properties);

    @AfterEach
    void tearDown() {
        parser.shutdown();
    }

    @Test
    @DisplayName("TXT/MD/HTML/CSV 文本抽取")
    void shouldParsePlainTextFormats() {
        assertTrue(parser.parse(bytes("项目验收通过"), "说明.txt", "text/plain").contains("项目验收通过"));
        assertTrue(parser.parse(bytes("# 制度\n正文内容"), "制度.md", "text/markdown").contains("制度"));
        String html = "<html><body><h1>安全制度</h1><p>正文</p></body></html>";
        assertTrue(parser.parse(bytes(html), "制度.html", "text/html").contains("安全制度"));
        assertTrue(parser.parse(bytes("姓名,年龄\n张三,30"), "花名册.csv", "text/csv").contains("张三"));
    }

    @Test
    @DisplayName("DOCX/XLSX/PPTX/PDF 文本抽取")
    void shouldParseOfficeDocuments() throws Exception {
        assertTrue(parser.parse(docx(), "报告.docx", null).contains("验收报告"));
        assertTrue(parser.parse(xlsx(), "预算.xlsx", null).contains("差旅费"));
        assertTrue(parser.parse(pptx(), "汇报.pptx", null).contains("第一章"));
        assertTrue(parser.parse(pdf(), "说明.pdf", null).contains("BlueWhale"));
    }

    @Test
    @DisplayName("JSON 档案快照按原文抽取")
    void shouldParseJsonSnapshot() {
        String json = "{\"version\":1,\"instance\":{\"title\":\"采购审批\"}}";
        String text = parser.parse(bytes(json), "archive-WORKFLOW-1.json", "application/json");
        assertTrue(text.contains("采购审批"));
    }

    @Test
    @DisplayName("损坏的 PDF 判定为不可重试")
    void shouldRejectCorruptedPdf() {
        FileParseException error = assertThrows(FileParseException.class,
                () -> parser.parse(new byte[]{1, 2, 3}, "bad.pdf", "application/pdf"));
        assertTrue(!error.isRetryable());
    }

    @Test
    @DisplayName("非白名单 MIME 判定为不支持")
    void shouldRejectUnsupportedMime() {
        byte[] zip = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
        FileParseException error = assertThrows(FileParseException.class,
                () -> parser.parse(zip, "包.zip", "application/zip"));
        assertTrue(!error.isRetryable());
        assertTrue(error.getMessage().contains("不支持"));
    }

    @Test
    @DisplayName("超出大小上限前置拒绝")
    void shouldRejectOversizedFile() {
        properties.getKnowledge().setMaxFileSize(4);
        FileParseException error = assertThrows(FileParseException.class,
                () -> parser.parse(bytes("1234567890"), "大.txt", "text/plain"));
        assertTrue(error.getMessage().contains("大小上限"));
    }

    /**
     * 构造 DOCX。
     *
     * @return 文件字节
     * @throws Exception 写入失败
     */
    private byte[] docx() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("项目验收报告正文");
            document.write(output);
            return output.toByteArray();
        }
    }

    /**
     * 构造 XLSX。
     *
     * @return 文件字节
     * @throws Exception 写入失败
     */
    private byte[] xlsx() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("预算");
            sheet.createRow(0).createCell(0).setCellValue("科目");
            sheet.getRow(0).createCell(1).setCellValue("金额");
            sheet.createRow(1).createCell(0).setCellValue("差旅费");
            sheet.getRow(1).createCell(1).setCellValue("1200");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    /**
     * 构造 PPTX。
     *
     * @return 文件字节
     * @throws Exception 写入失败
     */
    private byte[] pptx() throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSLFSlide slide = slideShow.createSlide();
            XSLFTextBox box = slide.createTextBox();
            box.setText("第一章 概述");
            slideShow.write(output);
            return output.toByteArray();
        }
    }

    /**
     * 构造 PDF。
     *
     * @return 文件字节
     * @throws Exception 写入失败
     */
    private byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(100, 700);
                stream.showText("BlueWhale Archive");
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    /**
     * 字符串转 UTF-8 字节。
     *
     * @param text 文本
     * @return 字节
     */
    private byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
