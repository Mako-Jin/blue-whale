package com.bluewhale.pdm.part.support;

import com.bluewhale.pdm.part.vo.PdmBomExportRowVO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * BOM Excel 导出（最小 XLSX 写入器，无第三方依赖）。
 *
 * <p>仅使用 inlineStr 与数值单元格，生成可直接由 Excel/WPS 打开的 xlsx 文件。</p>
 *
 * @author BlueWhale
 */
public final class PdmBomExcelSupport {

    /** 表头（与导出行字段顺序一致） */
    private static final String[] HEADERS = {
            "路径", "行号", "零件编码", "零件名称", "类型",
            "用量", "单位", "损耗率(%)", "材质", "规格", "备注",
    };

    /** XML 声明 */
    private static final String XML_HEADER =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    /**
     * 工具类禁止实例化
     */
    private PdmBomExcelSupport() {
    }

    /**
     * 生成 BOM xlsx 字节。
     *
     * @param sheetName 工作表名
     * @param rows      导出行
     * @return xlsx 字节
     */
    public static byte[] toXlsx(String sheetName, List<PdmBomExportRowVO> rows) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addEntry(zip, "[Content_Types].xml", contentTypes());
            addEntry(zip, "_rels/.rels", rootRels());
            addEntry(zip, "xl/workbook.xml", workbook(sheetName));
            addEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            addEntry(zip, "xl/worksheets/sheet1.xml", sheet(rows));
        } catch (IOException exception) {
            throw new IllegalStateException("生成 BOM Excel 失败", exception);
        }
        return output.toByteArray();
    }

    /**
     * 写入 Zip 条目（固定时间戳，便于测试稳定）。
     *
     * @param zip      Zip 流
     * @param name     条目名
     * @param content  文本内容
     * @throws IOException 写入失败
     */
    private static void addEntry(ZipOutputStream zip, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /**
     * 构建工作表 XML。
     *
     * @param rows 导出行
     * @return 工作表 XML
     */
    private static String sheet(List<PdmBomExportRowVO> rows) {
        StringBuilder xml = new StringBuilder(4096);
        xml.append(XML_HEADER)
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetData>");
        appendRow(xml, 1, HEADERS);
        int rowIndex = 2;
        for (PdmBomExportRowVO row : rows) {
            appendRow(xml, rowIndex, new String[]{
                    text(row.getPath()), text(row.getLineNo()), text(row.getPartNo()), text(row.getPartName()),
                    text(row.getPartType()), text(row.getQty()), text(row.getUom()), text(row.getLossRate()),
                    text(row.getMaterial()), text(row.getSpec()), text(row.getRemark()),
            });
            rowIndex++;
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    /**
     * 追加一行。
     *
     * @param xml   缓冲
     * @param row   行号（从 1 开始）
     * @param cells 单元格文本
     */
    private static void appendRow(StringBuilder xml, int row, String[] cells) {
        xml.append("<row r=\"").append(row).append("\">");
        for (int index = 0; index < cells.length; index++) {
            String reference = columnName(index) + row;
            String value = cells[index];
            xml.append("<c r=\"").append(reference).append("\"");
            if (isNumeric(value)) {
                xml.append("><v>").append(value).append("</v></c>");
            } else {
                xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(value))
                        .append("</t></is></c>");
            }
        }
        xml.append("</row>");
    }

    /**
     * 判断文本是否为数值单元格。
     *
     * @param value 文本
     * @return 是否数值
     */
    private static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * 列序号转列名。
     *
     * @param index 从 0 开始
     * @return 列名
     */
    private static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int value = index;
        do {
            name.insert(0, (char) ('A' + value % 26));
            value = value / 26 - 1;
        } while (value >= 0);
        return name.toString();
    }

    /**
     * 空值转空串。
     *
     * @param value 值
     * @return 文本
     */
    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    /**
     * 转义 XML 特殊字符。
     *
     * @param value 文本
     * @return 转义文本
     */
    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 构建 Content Types。
     *
     * @return XML
     */
    private static String contentTypes() {
        return XML_HEADER
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "</Types>";
    }

    /**
     * 构建包关系。
     *
     * @return XML
     */
    private static String rootRels() {
        return XML_HEADER
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
                + "Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    /**
     * 构建工作簿。
     *
     * @param sheetName 工作表名
     * @return XML
     */
    private static String workbook(String sheetName) {
        return XML_HEADER
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + escape(limit(sheetName)) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    /**
     * 构建工作簿关系。
     *
     * @return XML
     */
    private static String workbookRels() {
        return XML_HEADER
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" "
                + "Target=\"worksheets/sheet1.xml\"/>"
                + "</Relationships>";
    }

    /**
     * 截断工作表名（Excel 上限 31 字符）。
     *
     * @param name 名称
     * @return 名称
     */
    private static String limit(String name) {
        if (name == null || name.isBlank()) {
            return "BOM";
        }
        return name.length() > 31 ? name.substring(0, 31) : name;
    }
}
