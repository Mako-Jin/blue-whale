package com.bluewhale.pdm.part.support;

import com.bluewhale.pdm.part.vo.PdmBomExportRowVO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BOM Excel 写入器单元测试。
 *
 * @author BlueWhale
 */
class PdmBomExcelSupportTest {

    /**
     * 生成的 xlsx 可解压且包含表头、数值与转义文本。
     *
     * @throws IOException 读取失败
     */
    @Test
    void toXlsxShouldContainHeaderAndEscapedCells() throws IOException {
        PdmBomExportRowVO row = new PdmBomExportRowVO();
        row.setPath("1");
        row.setPartNo("A&B");
        row.setPartName("成品");
        row.setQty(new BigDecimal("2"));
        row.setUom("PCS");

        byte[] bytes = PdmBomExcelSupport.toXlsx("BOM-P-100", List.of(row));

        assertTrue(bytes.length > 4);
        assertEquals(0x50, bytes[0]);
        assertEquals(0x4B, bytes[1]);

        Map<String, String> entries = unzip(bytes);
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"));
        String sheet = entries.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("零件编码"));
        assertTrue(sheet.contains("A&amp;B"));
        assertTrue(sheet.contains("<v>2</v>"));
        String workbook = entries.get("xl/workbook.xml");
        assertTrue(workbook.contains("BOM-P-100"));
    }

    /**
     * 解压 xlsx 为条目文本。
     *
     * @param bytes xlsx 字节
     * @return 条目名 → 文本
     * @throws IOException 读取失败
     */
    private Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes),
                StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                entry = zip.getNextEntry();
            }
        }
        return entries;
    }
}
