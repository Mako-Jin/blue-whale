package com.bluewhale.job.scheduler.handler;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.po.JobLogPo;
import com.bluewhale.job.mapper.JobLogMapper;
import com.bluewhale.job.scheduler.archive.FileUploadVO;
import com.bluewhale.job.scheduler.archive.JobArchiveClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobLogArchiveHandler} 归档导出与清理单元测试。
 *
 * @author BlueWhale
 */
class JobLogArchiveHandlerTest {

    private final JobLogMapper jobLogMapper = mock(JobLogMapper.class);

    private final JobArchiveClient jobArchiveClient = mock(JobArchiveClient.class);

    private final JobProperties jobProperties = new JobProperties();

    private final JobLogArchiveHandler handler =
            new JobLogArchiveHandler(jobLogMapper, jobProperties, jobArchiveClient);

    @Test
    @DisplayName("execute：上传成功才逻辑删除该批并返回回执")
    void shouldDeleteOnlyAfterUploadSuccess() {
        when(jobLogMapper.selectList(any())).thenReturn(List.of(log(1L), log(2L)));
        when(jobArchiveClient.upload(anyString(), any())).thenReturn(uploadResult());

        String result = handler.execute(null);

        verify(jobArchiveClient).upload(anyString(), any());
        verify(jobLogMapper).deleteByIds(any());
        assertTrue(result.contains("归档完成"));
        assertTrue(result.contains("bluewhale/job-log/archived.csv"));
        assertTrue(result.contains("2 条"));
    }

    @Test
    @DisplayName("execute：上传失败不删除任何数据且返回失败摘要")
    void shouldKeepDataWhenUploadFails() {
        when(jobLogMapper.selectList(any())).thenReturn(List.of(log(1L)));
        when(jobArchiveClient.upload(anyString(), any())).thenThrow(new BizException("whale-file 不可达"));

        String result = handler.execute(null);

        verify(jobLogMapper, never()).deleteByIds(any());
        assertTrue(result.contains("归档失败"));
        assertTrue(result.contains("未清理"));
        assertTrue(result.contains("whale-file 不可达"));
    }

    @Test
    @DisplayName("execute：无过期数据时不上传、不删除")
    void shouldDoNothingWhenNoExpiredData() {
        when(jobLogMapper.selectList(any())).thenReturn(List.of());

        String result = handler.execute(null);

        assertEquals("无可归档的 job_log", result);
        verify(jobArchiveClient, never()).upload(anyString(), any());
        verify(jobLogMapper, never()).deleteByIds(any());
    }

    /**
     * 构造测试用执行日志
     *
     * @param id 主键
     * @return 执行日志实体
     */
    private static JobLogPo log(Long id) {
        JobLogPo jobLog = new JobLogPo();
        jobLog.setId(id);
        jobLog.setJobId(1L);
        jobLog.setJobName("demo");
        jobLog.setHandlerBean("demoJobHandler");
        return jobLog;
    }

    /**
     * 构造测试用上传结果
     *
     * @return 上传结果视图
     */
    private static FileUploadVO uploadResult() {
        FileUploadVO vo = new FileUploadVO();
        vo.setBucket("bluewhale");
        vo.setObjectKey("job-log/archived.csv");
        return vo;
    }
}
