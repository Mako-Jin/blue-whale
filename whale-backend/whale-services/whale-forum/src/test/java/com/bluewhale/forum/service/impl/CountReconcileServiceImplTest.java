package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ReconcileItemVO;
import com.bluewhale.forum.dto.ReconcileReportVO;
import com.bluewhale.forum.dto.ThreadCountDrift;
import com.bluewhale.forum.entity.ForumCountReconcile;
import com.bluewhale.forum.mapper.ForumCountReconcileMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.support.ForumMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 计数对账单测：差异识别、dry-run 不写库、修正与轨迹一致、明细截断。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class CountReconcileServiceImplTest {

    @Mock
    private ForumThreadMapper threadMapper;

    @Mock
    private ForumCountReconcileMapper reconcileMapper;

    @Mock
    private ForumMetrics metrics;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private CountReconcileServiceImpl reconcileService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("dry-run：只报告差异，不改计数")
    void dryRunReportsWithoutFixing() {
        when(threadMapper.selectCountDrift(1L, 500)).thenReturn(List.of(drift(11L, 5, 7, 2, 2, 0, 0)));

        ReconcileReportVO report = reconcileService.reconcile(false, null, 9001L);

        assertThat(report.getDiffCount()).isEqualTo(1);
        assertThat(report.getFixedCount()).isZero();
        assertThat(report.getDryRun()).isTrue();
        assertThat(report.getItems()).hasSize(1);
        ReconcileItemVO item = report.getItems().get(0);
        assertThat(item.getMetric()).isEqualTo("REPLY");
        assertThat(item.getBefore()).isEqualTo(5);
        assertThat(item.getActual()).isEqualTo(7);
        assertThat(item.getFixed()).isFalse();
        verify(threadMapper, never()).fixCounts(anyLong(), anyLong(), anyInt(), anyInt(), anyInt());
        ArgumentCaptor<ForumCountReconcile> captor = ArgumentCaptor.forClass(ForumCountReconcile.class);
        verify(reconcileMapper).insert(captor.capture());
        assertThat(captor.getValue().getFixed()).isZero();
        assertThat(captor.getValue().getBatchNo()).isNotBlank();
        verify(metrics).recordReconcileDiff("REPLY");
    }

    @Test
    @DisplayName("修正：按真实值写回并留 fixed=1 轨迹")
    void fixWritesBackAndMarksTrace() {
        when(threadMapper.selectCountDrift(1L, 500)).thenReturn(List.of(drift(11L, 5, 7, 2, 2, 0, 0)));

        ReconcileReportVO report = reconcileService.reconcile(true, null, 9001L);

        assertThat(report.getDiffCount()).isEqualTo(1);
        assertThat(report.getFixedCount()).isEqualTo(1);
        assertThat(report.getDryRun()).isFalse();
        verify(threadMapper).fixCounts(11L, 1L, 7, 2, 0);
        ArgumentCaptor<ForumCountReconcile> captor = ArgumentCaptor.forClass(ForumCountReconcile.class);
        verify(reconcileMapper).insert(captor.capture());
        assertThat(captor.getValue().getFixed()).isEqualTo(1);
        assertThat(captor.getValue().getOperatorId()).isEqualTo(9001L);
        assertThat(captor.getValue().getValueActual()).isEqualTo(7);
    }

    @Test
    @DisplayName("无漂移：不留轨迹、不改计数")
    void noDriftDoesNothing() {
        when(threadMapper.selectCountDrift(1L, 500)).thenReturn(List.of());

        ReconcileReportVO report = reconcileService.reconcile(true, null, null);

        assertThat(report.getDiffCount()).isZero();
        assertThat(report.getItems()).isEmpty();
        verify(reconcileMapper, never()).insert(any(ForumCountReconcile.class));
        verify(threadMapper, never()).fixCounts(anyLong(), anyLong(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("三项同时漂移：差异按项计数，明细只回传前 20 条")
    void countsEachMetricAndTruncatesItems() {
        List<ThreadCountDrift> drifts = new ArrayList<>();
        for (long id = 1; id <= 25; id++) {
            drifts.add(drift(id, 0, 1, 0, 0, 0, 0));
        }
        when(threadMapper.selectCountDrift(1L, 500)).thenReturn(drifts);

        ReconcileReportVO report = reconcileService.reconcile(false, null, null);

        assertThat(report.getDiffCount()).isEqualTo(25);
        assertThat(report.getItems()).hasSize(20);
        assertThat(report.getTruncated()).isTrue();
        verify(reconcileMapper, times(25)).insert(any(ForumCountReconcile.class));
    }

    /**
     * 构造漂移行。
     *
     * @param threadId 帖子ID
     * @param beforeReply 展示回复数
     * @param actualReply 真实回复数
     * @param beforeLike 展示投花数
     * @param actualLike 真实投花数
     * @param beforeCollect 展示收藏数
     * @param actualCollect 真实收藏数
     * @return 漂移行
     */
    private ThreadCountDrift drift(long threadId, int beforeReply, int actualReply,
                                   int beforeLike, int actualLike,
                                   int beforeCollect, int actualCollect) {
        ThreadCountDrift drift = new ThreadCountDrift();
        drift.setThreadId(threadId);
        drift.setReplyCount(beforeReply);
        drift.setActualReply(actualReply);
        drift.setLikeCount(beforeLike);
        drift.setActualLike(actualLike);
        drift.setCollectCount(beforeCollect);
        drift.setActualCollect(actualCollect);
        return drift;
    }
}
