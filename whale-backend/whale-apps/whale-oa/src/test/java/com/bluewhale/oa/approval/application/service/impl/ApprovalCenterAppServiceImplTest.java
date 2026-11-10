package com.bluewhale.oa.approval.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalActionCommand;
import com.bluewhale.oa.approval.application.command.ApprovalCenterQuery;
import com.bluewhale.oa.approval.application.command.ApprovalUrgeCommand;
import com.bluewhale.oa.approval.application.event.ApprovalUrgedEvent;
import com.bluewhale.oa.approval.application.service.ApprovalBusinessDetailProvider;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.domain.gateway.WorkflowApprovalGateway;
import com.bluewhale.oa.approval.domain.model.ApprovalActor;
import com.bluewhale.oa.approval.domain.model.WorkflowInstanceSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowTaskQuery;
import com.bluewhale.oa.approval.domain.model.WorkflowTaskSnapshot;
import com.bluewhale.oa.approval.domain.repository.ApprovalInstanceRepository;
import com.bluewhale.oa.common.OaErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalCenterAppServiceImpl} 列表过滤、动作归属与幂等测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ApprovalCenterAppServiceImplTest {

    private static final int STATUS_RUNNING = 0;

    private static final int STATUS_APPROVED = 1;

    @Mock
    private WorkflowApprovalGateway workflowApprovalGateway;

    @Mock
    private ApprovalInstanceRepository approvalInstanceRepository;

    @Mock
    private ApprovalInstanceAppService approvalInstanceAppService;

    @Mock
    private ObjectProvider<ApprovalBusinessDetailProvider> businessDetailProviders;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private ApprovalCenterAppServiceImpl approvalCenterAppService;

    @Test
    @DisplayName("urge：发起人催办后发布催办事件")
    void shouldPublishUrgedEvent() {
        WorkflowInstanceSnapshot instance = new WorkflowInstanceSnapshot();
        instance.setId(1L);
        instance.setInitiator("zhangsan");
        instance.setStatus(STATUS_RUNNING);
        when(workflowApprovalGateway.getInstance(1L)).thenReturn(instance);
        ApprovalUrgeCommand command = new ApprovalUrgeCommand();
        command.setTargetAccount("lisi");
        command.setComment("请尽快审批");

        approvalCenterAppService.urge(1L, command, new ApprovalActor(1L, "zhangsan"));

        ArgumentCaptor<ApprovalUrgedEvent> captor = ArgumentCaptor.forClass(ApprovalUrgedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals("lisi", captor.getValue().targetAccount());
        assertEquals("请尽快审批", captor.getValue().comment());
    }

    @Test
    @DisplayName("pagePending：强制按当前账号与运行中过滤")
    void shouldFilterPendingByCurrentUser() {
        when(workflowApprovalGateway.pageTasks(any(WorkflowTaskQuery.class)))
                .thenReturn(PageResult.of(List.of(), 0, 1, 10));

        approvalCenterAppService.pagePending(new ApprovalActor(1L, "zhangsan"), new ApprovalCenterQuery());

        ArgumentCaptor<WorkflowTaskQuery> captor = ArgumentCaptor.forClass(WorkflowTaskQuery.class);
        verify(workflowApprovalGateway).pageTasks(captor.capture());
        assertEquals("zhangsan", captor.getValue().getAssignee());
        assertEquals(STATUS_RUNNING, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("approve：非任务归属人抛 40301 且不提交 Workflow")
    void shouldRejectWhenNotTaskOwner() {
        when(workflowApprovalGateway.getInstance(1L)).thenReturn(instanceWithTask(STATUS_RUNNING, "lisi"));

        BizException ex = assertThrows(BizException.class, () ->
                approvalCenterAppService.approve(1L, 2L, new ApprovalActionCommand(),
                        new ApprovalActor(1L, "zhangsan")));

        assertEquals(OaErrorCode.APPROVAL_ACTION_FORBIDDEN, ex.getCode());
        verify(workflowApprovalGateway, never()).completeTask(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("approve：任务已同意时幂等返回，不重复提交")
    void shouldBeIdempotentWhenAlreadyApproved() {
        when(workflowApprovalGateway.getInstance(1L)).thenReturn(instanceWithTask(STATUS_APPROVED, "zhangsan"));

        approvalCenterAppService.approve(1L, 2L, new ApprovalActionCommand(),
                new ApprovalActor(1L, "zhangsan"));

        verify(workflowApprovalGateway, never()).completeTask(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("cancel：非发起人抛 40301")
    void shouldRejectCancelByNonInitiator() {
        WorkflowInstanceSnapshot instance = new WorkflowInstanceSnapshot();
        instance.setId(1L);
        instance.setInitiator("boss");
        instance.setStatus(STATUS_RUNNING);
        when(workflowApprovalGateway.getInstance(1L)).thenReturn(instance);

        BizException ex = assertThrows(BizException.class, () ->
                approvalCenterAppService.cancel(1L, new ApprovalActor(1L, "zhangsan")));

        assertEquals(OaErrorCode.APPROVAL_ACTION_FORBIDDEN, ex.getCode());
        verify(workflowApprovalGateway, never()).cancelInstance(1L);
    }

    /**
     * 构造带单个任务的流程实例。
     *
     * @param taskStatus 任务状态
     * @param assignee 审批人账号
     * @return 流程实例快照
     */
    private static WorkflowInstanceSnapshot instanceWithTask(int taskStatus, String assignee) {
        WorkflowTaskSnapshot task = new WorkflowTaskSnapshot();
        task.setId(2L);
        task.setInstanceId(1L);
        task.setAssignee(assignee);
        task.setStatus(taskStatus);
        WorkflowInstanceSnapshot instance = new WorkflowInstanceSnapshot();
        instance.setId(1L);
        instance.setInitiator("initiator");
        instance.setStatus(STATUS_RUNNING);
        instance.setTasks(List.of(task));
        return instance;
    }
}
