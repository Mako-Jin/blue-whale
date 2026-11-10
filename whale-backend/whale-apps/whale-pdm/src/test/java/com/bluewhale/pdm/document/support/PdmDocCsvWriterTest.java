package com.bluewhale.pdm.document.support;

import com.bluewhale.pdm.document.entity.PdmDocDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图文档 CSV 生成器单元测试。
 *
 * @author BlueWhale
 */
class PdmDocCsvWriterTest {

    /**
     * 表头带 BOM，字段按 RFC 4180 转义，空值输出空串。
     */
    @Test
    @DisplayName("CSV 输出 BOM、表头与 RFC 4180 转义")
    void shouldEscapeFieldsAndPrefixBom() {
        PdmDocDocument document = new PdmDocDocument();
        document.setDocNo("DOC-1");
        document.setTitle("A,B\"C");
        document.setDocType("DRAWING");
        document.setStatus("RELEASED");
        document.setFolderId(5L);
        document.setLatestVersionNo("V1.0");
        document.setCreateTime(LocalDateTime.of(2026, 11, 6, 9, 30, 0));
        document.setUpdateTime(LocalDateTime.of(2026, 11, 6, 10, 0, 0));

        byte[] bytes = PdmDocCsvWriter.toCsvBytes(List.of(document));
        String csv = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF"));
        assertEquals("\uFEFF文档编号,标题,类型,状态,所属目录ID,零件号,设计师,最新版本号,创建时间,更新时间",
                csv.lines().findFirst().orElse(""));
        assertEquals("DOC-1,\"A,B\"\"C\",DRAWING,RELEASED,5,,,V1.0,2026-11-06 09:30:00,2026-11-06 10:00:00",
                csv.lines().skip(1).findFirst().orElse(""));
    }

    /**
     * 空列表仅输出表头。
     */
    @Test
    @DisplayName("空列表仅输出表头")
    void shouldRenderHeaderOnlyForEmptyList() {
        String csv = new String(PdmDocCsvWriter.toCsvBytes(List.of()), StandardCharsets.UTF_8);

        assertEquals(1L, csv.lines().count());
        assertTrue(csv.endsWith("\n"));
    }
}
