package com.bluewhale.audit.support;

import com.bluewhale.common.audit.entity.AuditLogRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计日志 CSV 生成单测：UTF-8 BOM、表头、{@code \n} 行尾与 CSV 字段转义。
 *
 * @author BlueWhale
 */
class AuditCsvWriterTest {

    /** UTF-8 BOM 三字节（EF BB BF） */
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Test
    void shouldPrependBomAndWriteHeaderWithLfLineEndings() {
        byte[] csv = AuditCsvWriter.toCsvBytes(List.of());

        assertThat(csv[0]).isEqualTo(BOM[0]);
        assertThat(csv[1]).isEqualTo(BOM[1]);
        assertThat(csv[2]).isEqualTo(BOM[2]);
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("\uFEFFid,traceId,userId");
        assertThat(text).endsWith("\n");
        assertThat(text).doesNotContain("\r");
    }

    @Test
    void shouldEscapeFieldsContainingDelimiterQuoteAndLineBreak() {
        AuditLogRecord record = new AuditLogRecord();
        record.setId(1L);
        record.setUsername("alice,admin");
        record.setOperation("say \"hi\"");
        record.setCreateTime(LocalDateTime.of(2026, 10, 19, 12, 0, 0));

        String text = new String(AuditCsvWriter.toCsvBytes(List.of(record)), StandardCharsets.UTF_8);

        assertThat(text).contains("\"alice,admin\"");
        assertThat(text).contains("\"say \"\"hi\"\"\"");
        assertThat(text).contains("2026-10-19 12:00:00");
    }

    @Test
    void shouldNeutralizeFormulaInjectionAndKeepSignedNumbers() {
        AuditLogRecord record = new AuditLogRecord();
        record.setId(3L);
        record.setUsername("=cmd|' /C calc'!A0");
        record.setOperation("@SUM(1+1)");
        record.setErrorMsg("+HYPERLINK(\"http://evil\")");
        record.setCostMs(-12L);

        String text = new String(AuditCsvWriter.toCsvBytes(List.of(record)), StandardCharsets.UTF_8);

        assertThat(text).contains("'=cmd|' /C calc'!A0");
        assertThat(text).contains("'@SUM(1+1)");
        assertThat(text).contains("'+HYPERLINK(\"\"http://evil\"\")");
        assertThat(text).doesNotContain("'-12");
    }

    @Test
    void shouldRenderNullFieldsAsEmptyCells() {
        AuditLogRecord record = new AuditLogRecord();
        record.setId(2L);

        String text = new String(AuditCsvWriter.toCsvBytes(List.of(record)), StandardCharsets.UTF_8);

        assertThat(text).contains("2,,,");
    }
}
