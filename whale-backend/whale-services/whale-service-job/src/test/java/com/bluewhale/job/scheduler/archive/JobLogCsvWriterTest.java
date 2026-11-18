package com.bluewhale.job.scheduler.archive;

import com.bluewhale.job.po.JobLogPo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JobLogCsvWriter} 归档 CSV 生成与转义单元测试。
 *
 * @author BlueWhale
 */
class JobLogCsvWriterTest {

    @Test
    @DisplayName("write：含 UTF-8 BOM、表头与 \\n 行尾")
    void shouldWriteBomHeaderAndNewline() {
        String csv = JobLogCsvWriter.write(List.of());

        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("id,jobId,jobName,handlerBean,startTime,endTime,status,result,errorMsg\n"));
    }

    @Test
    @DisplayName("write：数据列与表头一致且时间按固定格式输出")
    void shouldWriteAlignedColumns() {
        JobLogPo log = new JobLogPo();
        log.setId(10L);
        log.setJobId(2L);
        log.setJobName("demo");
        log.setHandlerBean("demoJobHandler");
        log.setStartTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        log.setEndTime(LocalDateTime.of(2026, 1, 2, 3, 4, 6));
        log.setStatus(1);
        log.setResult("ok");

        String csv = JobLogCsvWriter.write(List.of(log));

        assertTrue(csv.contains("10,2,demo,demoJobHandler,2026-01-02 03:04:05,2026-01-02 03:04:06,1,ok,\n"));
    }

    @Test
    @DisplayName("write：含逗号/引号/换行的字段按 CSV 规则转义")
    void shouldEscapeSpecialCharacters() {
        JobLogPo log = new JobLogPo();
        log.setId(1L);
        log.setJobId(1L);
        log.setJobName("a,b");
        log.setHandlerBean("he said \"hi\"");
        log.setStartTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        log.setStatus(-1);
        log.setResult("line1\nline2");
        log.setErrorMsg("x");

        String csv = JobLogCsvWriter.write(List.of(log));

        assertTrue(csv.contains("\"a,b\""));
        assertTrue(csv.contains("\"he said \"\"hi\"\"\""));
        assertTrue(csv.contains("\"line1\nline2\""));
    }
}
