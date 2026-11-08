package com.bluewhale.pdm.change.service.impl;

import com.bluewhale.pdm.change.client.PdmChgMessageClient;
import com.bluewhale.pdm.change.entity.PdmChgNotice;
import com.bluewhale.pdm.change.mapper.PdmChgJobLockMapper;
import com.bluewhale.pdm.change.service.PdmChgNoticeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 变更到期生效任务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmChgEffectiveJobTest {

    @Mock
    private PdmChgNoticeService noticeService;

    @Mock
    private PdmChgJobLockMapper jobLockMapper;

    @Mock
    private PdmChgMessageClient messageClient;

    @InjectMocks
    private PdmChgEffectiveJob effectiveJob;

    /**
     * 未抢到锁时不扫描、不生效。
     */
    @Test
    void executeShouldSkipWhenLockNotAcquired() {
        when(jobLockMapper.acquire(anyString(), anyString(), any())).thenReturn(0);

        effectiveJob.execute();

        verify(noticeService, never()).dueNotices();
        verify(jobLockMapper, never()).release(anyString(), anyString());
    }

    /**
     * 抢到锁后逐条生效并释放锁。
     */
    @Test
    void executeShouldEffectiveDueNotices() {
        when(jobLockMapper.acquire(anyString(), anyString(), any())).thenReturn(1);
        when(noticeService.dueNotices()).thenReturn(List.of(notice(1L)));
        when(noticeService.effective(1L, null)).thenReturn(null);

        effectiveJob.execute();

        verify(noticeService).effective(1L, null);
        verify(jobLockMapper).release(anyString(), anyString());
    }

    /**
     * 单条失败发送告警但继续，并释放锁。
     */
    @Test
    void executeShouldAlertOnFailure() {
        when(jobLockMapper.acquire(anyString(), anyString(), any())).thenReturn(1);
        when(noticeService.dueNotices()).thenReturn(List.of(notice(2L)));
        when(noticeService.effective(2L, null)).thenThrow(new RuntimeException("生效失败"));

        effectiveJob.execute();

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> recipients = ArgumentCaptor.forClass(List.class);
        verify(messageClient).send(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), recipients.capture());
        verify(jobLockMapper).release(anyString(), anyString());
    }

    /**
     * 构造到期通知。
     */
    private static PdmChgNotice notice(Long id) {
        PdmChgNotice notice = new PdmChgNotice();
        notice.setId(id);
        notice.setNoticeNo("CN-" + id);
        notice.setEffectiveFrom(LocalDateTime.now().minusHours(1));
        return notice;
    }

    /**
     * 定时入口开关关闭时空转，返回 skipped；手工入口 execute() 不受影响。
     */
    @Test
    void scheduledRunShouldSkipWhenDisabled() {
        ReflectionTestUtils.setField(effectiveJob, "effectiveJobEnabled", false);

        assertEquals("skipped: effective-job disabled", effectiveJob.scheduledRun());
        verify(noticeService, never()).dueNotices();
    }

    /**
     * 定时入口返回执行摘要：到期数、生效数、失败数。
     */
    @Test
    void scheduledRunShouldReturnSummary() {
        ReflectionTestUtils.setField(effectiveJob, "effectiveJobEnabled", true);
        when(jobLockMapper.acquire(anyString(), anyString(), any())).thenReturn(1);
        when(noticeService.dueNotices()).thenReturn(List.of(notice(3L), notice(4L)));
        when(noticeService.effective(3L, null)).thenReturn(null);
        when(noticeService.effective(4L, null)).thenThrow(new RuntimeException("生效失败"));

        assertEquals("due=2, effective=1, failed=1", effectiveJob.scheduledRun());
    }
}
