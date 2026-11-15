package com.bluewhale.form.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormDataExportSupport} CSV 导出安全单元测试。
 *
 * @author BlueWhale
 */
class FormDataExportSupportTest {

    @Test
    @DisplayName("csv：以公式字符开头的单元格前置单引号，数值保持原样")
    void shouldNeutralizeFormulaInCsv() {
        byte[] content = FormDataExportSupport.csv(List.of("字段一", "字段二"),
                List.of(
                        List.of("=1+1", "normal"),
                        List.of("@SUM(A1:A2)", "-3.14"),
                        List.of("-abc", "+100")));
        String text = new String(content, StandardCharsets.UTF_8);

        assertTrue(text.contains("'=1+1"));
        assertTrue(text.contains("'@SUM(A1:A2)"));
        assertTrue(text.contains("'-abc"));
        assertFalse(text.contains("'normal"));
        assertFalse(text.contains("'-3.14"));
        assertFalse(text.contains("'+100"));
    }

    @Test
    @DisplayName("csv：含制表符的单元格加引号")
    void shouldQuoteCellWithTab() {
        byte[] content = FormDataExportSupport.csv(List.of("字段"),
                List.of(List.of("a\tb")));
        String text = new String(content, StandardCharsets.UTF_8);

        assertTrue(text.contains("\"a\tb\""));
    }

    @Test
    @DisplayName("xlsx：按表头生成列宽定义")
    void shouldWriteColumnWidths() throws IOException {
        byte[] content = FormDataExportSupport.xlsx("表单", List.of("金额", "备注说明"), List.of());
        String sheetXml = "";
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    sheetXml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }

        assertTrue(sheetXml.contains("<cols>"));
        assertTrue(sheetXml.contains("<col min=\"1\" max=\"1\""));
        assertTrue(sheetXml.contains("<sheetData>"));
    }

    @Test
    @DisplayName("maskSensitive：长文本保留首尾，短文本统一掩码")
    void shouldMaskSensitiveText() {
        org.junit.jupiter.api.Assertions.assertEquals("138****5678",
                FormDataExportSupport.maskSensitive("13812345678"));
        org.junit.jupiter.api.Assertions.assertEquals("12***89",
                FormDataExportSupport.maskSensitive("123456789"));
        org.junit.jupiter.api.Assertions.assertEquals("***", FormDataExportSupport.maskSensitive("abc"));
        org.junit.jupiter.api.Assertions.assertEquals("", FormDataExportSupport.maskSensitive(null));
        org.junit.jupiter.api.Assertions.assertEquals("", FormDataExportSupport.maskSensitive(""));
    }
}
