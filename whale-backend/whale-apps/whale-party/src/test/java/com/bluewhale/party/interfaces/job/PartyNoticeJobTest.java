package com.bluewhale.party.interfaces.job;

import com.bluewhale.party.integration.application.service.NotifyAppService;
import com.bluewhale.party.integration.application.view.NoticeScanResultView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 通知扫描任务入口测试。
 *
 * @author BlueWhale
 */
class PartyNoticeJobTest {

    private NotifyAppService notifyAppService;

    private PartyNoticeJob job;

    @BeforeEach
    void setUp() {
        notifyAppService = Mockito.mock(NotifyAppService.class);
        job = new PartyNoticeJob(notifyAppService);
    }

    @Test
    @DisplayName("扫描成功：返回党务与工会发送摘要")
    void shouldReturnSummary() {
        NoticeScanResultView party = new NoticeScanResultView();
        party.setTotalSent(5);
        NoticeScanResultView union = new NoticeScanResultView();
        union.setTotalSent(2);
        when(notifyAppService.scanParty()).thenReturn(party);
        when(notifyAppService.scanUnion()).thenReturn(union);

        assertEquals("party=5, union=2", job.scan());
    }

    @Test
    @DisplayName("党务扫描失败：上抛且不再执行工会扫描")
    void shouldPropagatePartyFailure() {
        when(notifyAppService.scanParty()).thenThrow(new IllegalStateException("消息服务不可用"));

        assertThrows(IllegalStateException.class, () -> job.scan());
        Mockito.verify(notifyAppService, Mockito.never()).scanUnion();
    }
}
