package com.bluewhale.form.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormDataImportSupport} CSV 解析、模板生成与单元格转换单元测试。
 *
 * @author BlueWhale
 */
class FormDataImportSupportTest {

    @Test
    @DisplayName("parseCsv：去除 UTF-8 BOM")
    void shouldStripBom() {
        List<List<String>> rows = FormDataImportSupport.parseCsv(bytes("\uFEFF姓名,金额\r\n张三,12.5\r\n"));

        assertEquals(List.of("姓名", "金额"), rows.get(0));
        assertEquals("张三", rows.get(1).get(0));
    }

    @Test
    @DisplayName("parseCsv：引号内的逗号与换行不拆分单元格")
    void shouldParseQuotedCellWithCommaAndNewline() {
        List<List<String>> rows = FormDataImportSupport.parseCsv(bytes("a,\"b,c\nd\",e"));

        assertEquals(1, rows.size());
        assertEquals(List.of("a", "b,c\nd", "e"), rows.get(0));
    }

    @Test
    @DisplayName("parseCsv：双写引号还原为单个引号")
    void shouldUnescapeDoubleQuote() {
        List<List<String>> rows = FormDataImportSupport.parseCsv(bytes("\"a\"\"b\",c"));

        assertEquals(List.of("a\"b", "c"), rows.get(0));
    }

    @Test
    @DisplayName("parseCsv：兼容 CRLF 与单独 CR 行分隔")
    void shouldParseCrLfAndCrLineBreaks() {
        List<List<String>> rows = FormDataImportSupport.parseCsv(bytes("a,b\r\nc,d\re,f"));

        assertEquals(3, rows.size());
        assertEquals("c", rows.get(1).get(0));
        assertEquals("e", rows.get(2).get(0));
    }

    @Test
    @DisplayName("parseCsv：忽略完全空行")
    void shouldIgnoreBlankLines() {
        List<List<String>> rows = FormDataImportSupport.parseCsv(bytes("a,b\n\n  ,  \nc,d\n"));

        assertEquals(2, rows.size());
        assertEquals("c", rows.get(1).get(0));
    }

    @Test
    @DisplayName("parseCsv：短行按最宽行补空串")
    void shouldPadShortRow() {
        List<List<String>> rows = FormDataImportSupport.parseCsv(bytes("a,b\nc"));

        assertEquals(List.of("c", ""), rows.get(1));
    }

    @Test
    @DisplayName("parseCsv：未闭合的引号报错")
    void shouldRejectUnclosedQuote() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormDataImportSupport.parseCsv(bytes("a,\"b")));

        assertEquals("CSV 存在未闭合的引号", error.getMessage());
    }

    @Test
    @DisplayName("parseCsv：超过 5000 行报错")
    void shouldRejectTooManyRows() {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < 5001; index++) {
            builder.append(index).append('\n');
        }

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormDataImportSupport.parseCsv(bytes(builder.toString())));

        assertTrue(error.getMessage().contains("行数超过上限"));
    }

    @Test
    @DisplayName("convert：number 解析合法数字并拒绝非法值")
    void shouldConvertNumberAndRejectInvalid() {
        FormField field = new FormField("amount", "金额", FormDataType.NUMBER, true, null);

        assertEquals(new BigDecimal("12.50"), FormDataImportSupport.convert(field, " 12.50 "));
        assertNull(FormDataImportSupport.convert(field, "   "));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormDataImportSupport.convert(field, "abc"));
        assertEquals("字段 金额 的值不是合法数字", error.getMessage());
    }

    @Test
    @DisplayName("convert：bool 识别真值/假值文本并拒绝其他值")
    void shouldConvertBoolAndRejectInvalid() {
        FormField field = new FormField("enabled", "启用", FormDataType.BOOL, false, null);

        assertEquals(Boolean.TRUE, FormDataImportSupport.convert(field, "是"));
        assertEquals(Boolean.TRUE, FormDataImportSupport.convert(field, "YES"));
        assertEquals(Boolean.FALSE, FormDataImportSupport.convert(field, "0"));
        assertEquals(Boolean.FALSE, FormDataImportSupport.convert(field, "no"));

        assertThrows(IllegalArgumentException.class,
                () -> FormDataImportSupport.convert(field, "maybe"));
    }

    @Test
    @DisplayName("convert：date 去空白，text 原样返回")
    void shouldKeepDateTrimmedAndTextAsIs() {
        FormField date = new FormField("day", "日期", FormDataType.DATE, false, null);
        FormField text = new FormField("name", "名称", FormDataType.TEXT, false, 64);

        assertEquals("2026-01-02", FormDataImportSupport.convert(date, " 2026-01-02 "));
        assertEquals("  张三  ", FormDataImportSupport.convert(text, "  张三  "));
    }

    @Test
    @DisplayName("convert：attachment 不支持批量导入")
    void shouldRejectAttachment() {
        FormField field = new FormField("files", "附件", FormDataType.ATTACHMENT, false, null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormDataImportSupport.convert(field, "a.pdf"));

        assertEquals("字段 附件 不支持批量导入", error.getMessage());
    }

    @Test
    @DisplayName("template：带 BOM 且首行为表头")
    void shouldGenerateTemplateWithBomAndHeader() {
        byte[] content = FormDataImportSupport.template(List.of("姓名", "金额"));

        assertEquals((byte) 0xEF, content[0]);
        assertEquals((byte) 0xBB, content[1]);
        assertEquals((byte) 0xBF, content[2]);
        assertEquals("姓名,金额\r\n", new String(content, StandardCharsets.UTF_8).substring(1));
    }

    @Test
    @DisplayName("importable：基础类型可导入，附件/子表不可导入")
    void shouldReportImportableTypes() {
        assertTrue(FormDataImportSupport.importable(FormDataType.TEXT));
        assertTrue(FormDataImportSupport.importable(FormDataType.BOOL));
        assertFalse(FormDataImportSupport.importable(FormDataType.ATTACHMENT));
        assertFalse(FormDataImportSupport.importable(FormDataType.SUBTABLE));
        assertFalse(FormDataImportSupport.importable(null));
    }

    /**
     * 按 UTF-8 编码文本
     *
     * @param text 文本
     * @return 字节数组
     */
    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
