package com.bluewhale.oa.approval.interfaces.job;

import com.bluewhale.oa.approval.application.command.ApprovalResultCommand;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.service.ApprovalNotificationAppService;
import com.bluewhale.oa.approval.domain.gateway.WorkflowApprovalGateway;
import com.bluewhale.oa.approval.domain.model.ApprovalInstance;
import com.bluewhale.oa.approval.domain.model.WorkflowInstanceStatusSnapshot;
import com.bluewhale.oa.approval.domain.repository.ApprovalInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalMaintenanceJob} 开关、阈值与状态映射测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ApprovalMaintenanceJobTest {

    @Mock
    private ApprovalInstanceRepository approvalInstanceRepository;

    @Mock
    private ApprovalInstanceAppService approvalInstanceAppService;

    @Mock
    private ApprovalNotificationAppService approvalNotificationAppService;

    @Mock
    private WorkflowApprovalGateway workflowApprovalGateway;

    private ApprovalMaintenanceJob job;

    /**
     * 构造任务实例并注入配置项。
     */
    @BeforeEach
    void setUp() {
        job = new ApprovalMaintenanceJob(approvalInstanceRepository, approvalInstanceAppService,
                approvalNotificationAppService, workflowApprovalGateway);
        ReflectionTestUtils.setField(job, "autoUrgeEnabled", true);
        ReflectionTestUtils.setField(job, "autoUrgeThresholdHours", 24L);
        ReflectionTestUtils.setField(job, "autoUrgeBatchSize", 100);
        ReflectionTestUtils.setField(job, "reconcileEnabled", true);
        ReflectionTestUtils.setField(job, "reconcileBatchSize", 200);
    }

    @Test
    @DisplayName("autoUrge：关闭开关时不查询")
    void shouldSkipAutoUrgeWhenDisabled() {
        ReflectionTestUtils.setField(job, "autoUrgeEnabled", false);

        job.autoUrge();

        verifyNoInteractions(approvalInstanceRepository, approvalNotificationAppService);
    }

    @Test
    @DisplayName("autoUrge：按阈值查询并逐条催办")
    void shouldAutoUrgeRunningInstances() {
        when(approvalInstanceRepository.findRunningSubmittedBefore(any(), anyInt()))
                .thenReturn(List.of(instance(1L, 101L), instance(2L, 102L)));

        job.autoUrge();

        verify(approvalInstanceRepository).findRunningSubmittedBefore(any(), eq(100));
        verify(approvalNotificationAppService).notifyAutoUrge(101L);
        verify(approvalNotificationAppService).notifyAutoUrge(102L);
    }

    @Test
    @DisplayName("reconcile：只回写终态，运行中忽略")
    void shouldSyncOnlyTerminalStatuses() {
        when(approvalInstanceRepository.findRunningWithWorkflow(200))
                .thenReturn(List.of(instance(1L, 101L), instance(2L, 102L)));
        when(workflowApprovalGateway.listInstanceStatuses(any()))
                .thenReturn(List.of(status(101L, 1), status(102L, 0)));

        job.reconcile();

        ArgumentCaptor<ApprovalResultCommand> captor = ArgumentCaptor.forClass(ApprovalResultCommand.class);
        verify(approvalInstanceAppService, times(1)).syncResult(eq(1L), captor.capture());
        assertEquals("APPROVED", captor.getValue().getStatus());
        verify(approvalInstanceAppService, never()).syncResult(eq(2L), any());
    }

    @Test
    @DisplayName("reconcile：单条回写失败不中断后续记录")
    void shouldContinueWhenOneSyncFails() {
        when(approvalInstanceRepository.findRunningWithWorkflow(200))
                .thenReturn(List.of(instance(1L, 101L), instance(2L, 102L)));
        when(workflowApprovalGateway.listInstanceStatuses(any()))
                .thenReturn(List.of(status(101L, 2), status(102L, 3)));
        org.mockito.Mockito.doThrow(new IllegalStateException("冲突"))
                .when(approvalInstanceAppService).syncResult(eq(1L), any());

        job.reconcile();

        verify(approvalInstanceAppService).syncResult(eq(2L), any());
    }

    /**
     * 构造本地审批实例。
     *
     * @param id 本地ID
     * @param workflowId 流程实例ID
     * @return 审批实例
     */
    private static ApprovalInstance instance(Long id, Long workflowId) {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(id);
        instance.setWorkflowInstanceId(workflowId);
        return instance;
    }

    /**
     * 构造流程状态摘要。
     *
     * @param id 流程实例ID
     * @param status 状态
     * @return 状态摘要
     */
    private static WorkflowInstanceStatusSnapshot status(Long id, int status) {
        WorkflowInstanceStatusSnapshot snapshot = new WorkflowInstanceStatusSnapshot();
        snapshot.setId(id);
        snapshot.setStatus(status);
        return snapshot;
    }
}
