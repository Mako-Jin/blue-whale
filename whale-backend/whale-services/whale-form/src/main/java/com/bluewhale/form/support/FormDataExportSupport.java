package com.bluewhale.form.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 表单数据导出器（CSV / XLSX），纯 JDK 实现，不引入 EasyExcel / POI。
 *
 * <p>CSV 带 UTF-8 BOM（Excel 直接双击不乱码）；XLSX 为最小 OpenXML 包
 * （{@code [Content_Types].xml} + {@code _rels/.rels} + {@code xl/workbook.xml} +
 * {@code xl/_rels/workbook.xml.rels} + {@code xl/worksheets/sheet1.xml}），
 * 单元格一律以 {@code inlineStr} 内联字符串写出，避免共享字符串表。</p>
 *
 * @author BlueWhale
 */
public final class FormDataExportSupport {

    /** CSV 单元格分隔符 */
    private static final char CSV_SEPARATOR = ',';

    /** CSV 行分隔符（CRLF，兼容 Excel） */
    private static final String CSV_LINE = "\r\n";

    /** UTF-8 BOM */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** XLSX 工作表名最大长度（Excel 约束 31） */
    private static final int MAX_SHEET_NAME = 31;

    /** 单列字母上限（26 列一组，足够覆盖表单数据列） */
    private static final int ALPHABET = 26;

    /** XLSX 列宽下限（字符数） */
    private static final int MIN_COLUMN_WIDTH = 10;

    /** XLSX 列宽上限（字符数） */
    private static final int MAX_COLUMN_WIDTH = 50;

    /** XLSX 列宽相对表头显示宽度的冗余 */
    private static final int COLUMN_WIDTH_PADDING = 4;

    /** 宽字符（CJK 等）起始码点近似值 */
    private static final int WIDE_CHAR_START = 0x2E80;

    /** 工具类不允许实例化 */
    private FormDataExportSupport() {
    }

    /**
     * 生成 CSV 字节流（UTF-8 + BOM，CRLF 行分隔）
     *
     * @param headers 表头
     * @param rows 数据行（每行长度应与表头一致）
     * @return CSV 字节数组
     */
    public static byte[] csv(List<String> headers, List<List<String>> rows) {
        StringBuilder builder = new StringBuilder();
        appendCsvRow(builder, headers);
        for (List<String> row : rows) {
            appendCsvRow(builder, row);
        }
        byte[] body = builder.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, result, UTF8_BOM.length, body.length);
        return result;
    }

    /**
     * 生成最小 XLSX 字节流（单工作表，全部单元格为内联字符串）
     *
     * @param sheetName 工作表名（超长或含非法字符时自动归一化）
     * @param headers 表头
     * @param rows 数据行
     * @return XLSX 字节数组
     */
    public static byte[] xlsx(String sheetName, List<String> headers, List<List<String>> rows) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            writeEntry(zip, "[Content_Types].xml", contentTypes());
            writeEntry(zip, "_rels/.rels", packageRels());
            writeEntry(zip, "xl/workbook.xml", workbook(sheetName));
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            writeEntry(zip, "xl/worksheets/sheet1.xml", sheet(headers, rows));
        } catch (IOException e) {
            throw new IllegalStateException("生成 XLSX 失败", e);
        }
        return buffer.toByteArray();
    }

    /**
     * 导出格式对应的响应 Content-Type
     *
     * @param format 导出格式（{@code csv} / {@code xlsx}）
     * @return Content-Type 文本
     */
    public static String contentType(String format) {
        if ("xlsx".equalsIgnoreCase(format)) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        return "text/csv;charset=UTF-8";
    }

    /**
     * 导出格式对应的文件扩展名
     *
     * @param format 导出格式（{@code csv} / {@code xlsx}）
     * @return 扩展名（不含点）
     */
    public static String extension(String format) {
        return "xlsx".equalsIgnoreCase(format) ? "xlsx" : "csv";
    }

    /**
     * 追加一行 CSV（含按需转义）
     *
     * @param builder 目标字符串缓冲
     * @param cells 单元格列表
     */
    private static void appendCsvRow(StringBuilder builder, List<String> cells) {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                builder.append(CSV_SEPARATOR);
            }
            builder.append(escapeCsv(cells.get(index)));
        }
        builder.append(CSV_LINE);
    }

    /**
     * 转义单个 CSV 单元格（公式注入中和 + 含逗号/引号/换行/前后空格时加引号并双写内部引号）
     *
     * <p>以 {@code = + - @}、制表符或回车开头的文本会被 Excel/WPS 识别为公式；此处统一前置单引号
     * 作为文本前缀（纯数值的 {@code +/-} 数字不受影响，避免破坏负数与正数）。</p>
     *
     * @param value 原始值（可空）
     * @return 转义后的单元格文本
     */
    private static String escapeCsv(String value) {
        String text = neutralizeFormula(value == null ? "" : value);
        boolean quoted = text.indexOf(CSV_SEPARATOR) >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\t') >= 0
                || text.startsWith(" ") || text.endsWith(" ");
        if (!quoted) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    /**
     * 中和 CSV 公式注入：公式起始字符前置单引号，数值不受影响
     *
     * @param text 原始单元格文本
     * @return 中和后的文本
     */
    private static String neutralizeFormula(String text) {
        if (text.isEmpty()) {
            return text;
        }
        char first = text.charAt(0);
        boolean formulaStart = first == '=' || first == '@' || first == '\t' || first == '\r';
        boolean numeric = (first == '+' || first == '-') && isNumeric(text);
        if (formulaStart || ((first == '+' || first == '-') && !numeric)) {
            return "'" + text;
        }
        return text;
    }

    /**
     * 判断文本是否为可解析的十进制数值
     *
     * @param text 待判断文本
     * @return 数值返回 {@code true}
     */
    private static boolean isNumeric(String text) {
        try {
            new BigDecimal(text.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 写出一个 ZIP 条目
     *
     * @param zip 目标 ZIP 输出流
     * @param name 条目名
     * @param content 条目内容（UTF-8）
     * @throws IOException 写出失败
     */
    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /**
     * 构造 {@code [Content_Types].xml}
     *
     * @return 部件内容类型声明
     */
    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\""
                + " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-"
                + "officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd."
                + "openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "</Types>";
    }

    /**
     * 构造 {@code _rels/.rels}
     *
     * @return 包级关系
     */
    private static String packageRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/"
                + "2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    /**
     * 构造 {@code xl/workbook.xml}
     *
     * @param sheetName 工作表名
     * @return 工作簿定义
     */
    private static String workbook(String sheetName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + escapeXml(sanitizeSheetName(sheetName))
                + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    }

    /**
     * 构造 {@code xl/_rels/workbook.xml.rels}
     *
     * @return 工作簿关系
     */
    private static String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/"
                + "2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "</Relationships>";
    }

    /**
     * 构造工作表 XML（首行表头，随后数据行，单元格均为 inlineStr）
     *
     * @param headers 表头
     * @param rows 数据行
     * @return 工作表定义
     */
    private static String sheet(List<String> headers, List<List<String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append(columnWidths(headers))
                .append("<sheetData>");
        appendSheetRow(builder, 1, headers);
        int rowIndex = 2;
        for (List<String> row : rows) {
            appendSheetRow(builder, rowIndex, row);
            rowIndex++;
        }
        builder.append("</sheetData></worksheet>");
        return builder.toString();
    }

    /**
     * 构造 {@code <cols>} 列宽定义（按表头显示宽度估算，避免长文本显示为截断）
     *
     * @param headers 表头
     * @return 列宽定义片段；无表头时返回空串
     */
    private static String columnWidths(List<String> headers) {
        if (headers.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("<cols>");
        for (int index = 0; index < headers.size(); index++) {
            int width = Math.min(Math.max(displayWidth(headers.get(index)) + COLUMN_WIDTH_PADDING,
                    MIN_COLUMN_WIDTH), MAX_COLUMN_WIDTH);
            int column = index + 1;
            builder.append("<col min=\"").append(column).append("\" max=\"").append(column)
                    .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
        }
        return builder.append("</cols>").toString();
    }

    /**
     * 估算文本显示宽度（CJK 等宽字符按 2 个字符宽度计）
     *
     * @param text 文本（可空）
     * @return 显示宽度
     */
    private static int displayWidth(String text) {
        if (text == null) {
            return 0;
        }
        int width = 0;
        for (int index = 0; index < text.length(); index++) {
            width += text.charAt(index) >= WIDE_CHAR_START ? 2 : 1;
        }
        return width;
    }

    /**
     * 追加工作表一行
     *
     * @param builder 目标字符串缓冲
     * @param rowIndex 行号（从 1 开始）
     * @param cells 单元格列表
     */
    private static void appendSheetRow(StringBuilder builder, int rowIndex, List<String> cells) {
        builder.append("<row r=\"").append(rowIndex).append("\">");
        for (int index = 0; index < cells.size(); index++) {
            builder.append("<c r=\"").append(columnName(index)).append(rowIndex).append("\" t=\"inlineStr\">")
                    .append("<is><t xml:space=\"preserve\">").append(escapeXml(cells.get(index)))
                    .append("</t></is></c>");
        }
        builder.append("</row>");
    }

    /**
     * 计算列坐标字母（0 → A、25 → Z、26 → AA）
     *
     * @param index 列下标（从 0 开始）
     * @return Excel 列字母
     */
    private static String columnName(int index) {
        StringBuilder builder = new StringBuilder();
        int value = index;
        do {
            builder.insert(0, (char) ('A' + value % ALPHABET));
            value = value / ALPHABET - 1;
        } while (value >= 0);
        return builder.toString();
    }

    /**
     * 归一化工作表名（去除 Excel 非法字符并截断）
     *
     * @param sheetName 原始工作表名
     * @return 合法工作表名；入参为空时返回 {@code Sheet1}
     */
    private static String sanitizeSheetName(String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return "Sheet1";
        }
        String name = sheetName.replaceAll("[\\\\/:*?\\[\\]]", "_").trim();
        return name.length() > MAX_SHEET_NAME ? name.substring(0, MAX_SHEET_NAME) : name;
    }

    /**
     * 转义 XML 文本并剔除非法控制字符
     *
     * @param value 原始值（可空）
     * @return 可安全写入 XML 的文本
     */
    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&apos;");
                default -> {
                    if (ch == '\t' || ch == '\n' || ch == '\r' || ch >= 0x20) {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }
}
