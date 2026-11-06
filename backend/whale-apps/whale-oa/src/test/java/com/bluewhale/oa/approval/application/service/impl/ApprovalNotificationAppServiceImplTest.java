package com.bluewhale.oa.approval.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.domain.gateway.ApprovalNotifyGateway;
import com.bluewhale.oa.approval.domain.gateway.UserDirectoryGateway;
import com.bluewhale.oa.approval.domain.gateway.WorkflowApprovalGateway;
import com.bluewhale.oa.approval.domain.model.ApprovalInstance;
import com.bluewhale.oa.approval.domain.model.ApprovalNotifyMessage;
import com.bluewhale.oa.approval.domain.model.WorkflowCcQuery;
import com.bluewhale.oa.approval.domain.model.WorkflowCcSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowInstanceSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowTaskSnapshot;
import com.bluewhale.oa.approval.domain.repository.ApprovalInstanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalNotificationAppServiceImpl} 通知幂等键、接收人解析与异常兜底测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ApprovalNotificationAppServiceImplTest {

    private static final Long WORKFLOW_ID = 10L;

    @Mock
    private ApprovalInstanceRepository approvalInstanceRepository;

    @Mock
    private WorkflowApprovalGateway workflowApprovalGateway;

    @Mock
    private UserDirectoryGateway userDirectoryGateway;

    @Mock
    private ApprovalNotifyGateway approvalNotifyGateway;

    @InjectMocks
    private ApprovalNotificationAppServiceImpl approvalNotificationAppService;

    @Test
    @DisplayName("notifyTodo：按任务与接收人生成幂等键")
    void shouldNotifyTodoWithIdempotencyKey() {
        when(workflowApprovalGateway.getInstance(WORKFLOW_ID)).thenReturn(runningSnapshot("zhangsan"));
        when(approvalInstanceRepository.findByWorkflowInstanceId(WORKFLOW_ID))
                .thenReturn(Optional.of(localInstance()));
        when(userDirectoryGateway.findUserIdByUsername("zhangsan")).thenReturn(Optional.of(5L));

        approvalNotificationAppService.notifyTodo(WORKFLOW_ID);

        ApprovalNotifyMessage message = captureMessage();
        assertEquals("OA_TODO:10:20:5", message.getIdempotencyKey());
        assertEquals("OA_APPROVAL_TODO", message.getTemplateCode());
        assertEquals(List.of(5L), message.getUserIds());
    }

    @Test
    @DisplayName("notifyTodo：账号解析失败时跳过投递")
    void shouldSkipWhenAccountNotFound() {
        when(workflowApprovalGateway.getInstance(WORKFLOW_ID)).thenReturn(runningSnapshot("zhangsan"));
        when(approvalInstanceRepository.findByWorkflowInstanceId(WORKFLOW_ID)).thenReturn(Optional.empty());
        when(userDirectoryGateway.findUserIdByUsername("zhangsan")).thenReturn(Optional.empty());

        approvalNotificationAppService.notifyTodo(WORKFLOW_ID);

        verify(approvalNotifyGateway, never()).send(any());
    }

    @Test
    @DisplayName("notifyResult：通知发起人且结果文案为中文")
    void shouldNotifyResult() {
        when(approvalInstanceRepository.findByBusiness("LEAVE", 100L))
                .thenReturn(Optional.of(localInstance()));

        approvalNotificationAppService.notifyResult("LEAVE", 100L, "APPROVED");

        ApprovalNotifyMessage message = captureMessage();
        assertEquals("OA_RESULT:1:APPROVED", message.getIdempotencyKey());
        assertEquals(List.of(9L), message.getUserIds());
        assertEquals("通过", message.getPayload().get("result"));
    }

    @Test
    @DisplayName("notifyUrge：指定被催办人只通知该账号")
    void shouldNotifySpecifiedTarget() {
        when(workflowApprovalGateway.getInstance(WORKFLOW_ID)).thenReturn(runningSnapshot("zhangsan"));
        when(approvalInstanceRepository.findByWorkflowInstanceId(WORKFLOW_ID))
                .thenReturn(Optional.of(localInstance()));
        when(userDirectoryGateway.findUserIdByUsername("lisi")).thenReturn(Optional.of(6L));

        approvalNotificationAppService.notifyUrge(WORKFLOW_ID, "lisi", "请尽快审批", "boss");

        ApprovalNotifyMessage message = captureMessage();
        assertTrue(message.getIdempotencyKey().startsWith("OA_URGE:10:"));
        assertEquals(List.of(6L), message.getUserIds());
        assertEquals("请尽快审批", message.getPayload().get("comment"));
    }

    @Test
    @DisplayName("notifyCc：未读抄送人收到抄送通知")
    void shouldNotifyUnreadCc() {
        when(workflowApprovalGateway.getInstance(WORKFLOW_ID)).thenReturn(runningSnapshot("zhangsan"));
        when(approvalInstanceRepository.findByWorkflowInstanceId(WORKFLOW_ID))
                .thenReturn(Optional.of(localInstance()));
        WorkflowCcSnapshot cc = new WorkflowCcSnapshot();
        cc.setId(30L);
        cc.setInstanceId(WORKFLOW_ID);
        cc.setRecipient("wangwu");
        cc.setReadFlag(0);
        when(workflowApprovalGateway.pageCcs(any(WorkflowCcQuery.class)))
                .thenReturn(PageResult.of(List.of(cc), 1L, 1, 200));
        when(userDirectoryGateway.findUserIdByUsername("wangwu")).thenReturn(Optional.of(7L));

        approvalNotificationAppService.notifyCc(WORKFLOW_ID);

        ApprovalNotifyMessage message = captureMessage();
        assertEquals("OA_CC:10:30:7", message.getIdempotencyKey());
        assertEquals(List.of(7L), message.getUserIds());
    }

    @Test
    @DisplayName("notifyAutoUrge：排除发起人且按天幂等")
    void shouldExcludeInitiatorOnAutoUrge() {
        WorkflowInstanceSnapshot snapshot = runningSnapshot("zhangsan");
        WorkflowTaskSnapshot initiatorTask = new WorkflowTaskSnapshot();
        initiatorTask.setId(21L);
        initiatorTask.setAssignee("9");
        initiatorTask.setStatus(0);
        snapshot.setTasks(List.of(snapshot.getTasks().getFirst(), initiatorTask));
        when(workflowApprovalGateway.getInstance(WORKFLOW_ID)).thenReturn(snapshot);
        when(approvalInstanceRepository.findByWorkflowInstanceId(WORKFLOW_ID))
                .thenReturn(Optional.of(localInstance()));
        when(userDirectoryGateway.findUserIdByUsername("zhangsan")).thenReturn(Optional.of(5L));

        approvalNotificationAppService.notifyAutoUrge(WORKFLOW_ID);

        ApprovalNotifyMessage message = captureMessage();
        assertTrue(message.getIdempotencyKey().startsWith("OA_AUTO_URGE:10:"));
        assertEquals(List.of(5L), message.getUserIds());
        assertEquals("系统自动催办", message.getPayload().get("urger"));
    }

    @Test
    @DisplayName("发送异常时仅记录日志，不向调用方抛出")
    void shouldSwallowNotifyFailure() {
        when(workflowApprovalGateway.getInstance(WORKFLOW_ID)).thenReturn(runningSnapshot("zhangsan"));
        when(approvalInstanceRepository.findByWorkflowInstanceId(WORKFLOW_ID))
                .thenReturn(Optional.of(localInstance()));
        when(userDirectoryGateway.findUserIdByUsername("zhangsan")).thenReturn(Optional.of(5L));
        org.mockito.Mockito.doThrow(new IllegalStateException("消息服务不可用"))
                .when(approvalNotifyGateway).send(any());

        approvalNotificationAppService.notifyTodo(WORKFLOW_ID);

        verify(approvalNotifyGateway).send(any());
    }

    /**
     * 捕获最近一次通知消息。
     *
     * @return 通知消息
     */
    private ApprovalNotifyMessage captureMessage() {
        ArgumentCaptor<ApprovalNotifyMessage> captor = ArgumentCaptor.forClass(ApprovalNotifyMessage.class);
        verify(approvalNotifyGateway).send(captor.capture());
        return captor.getValue();
    }

    /**
     * 构造运行中的流程实例快照。
     *
     * @param assignee 待办人账号
     * @return 流程实例快照
     */
    private static WorkflowInstanceSnapshot runningSnapshot(String assignee) {
        WorkflowTaskSnapshot task = new WorkflowTaskSnapshot();
        task.setId(20L);
        task.setInstanceId(WORKFLOW_ID);
        task.setAssignee(assignee);
        task.setStatus(0);
        task.setNodeName("部门经理审批");
        WorkflowInstanceSnapshot snapshot = new WorkflowInstanceSnapshot();
        snapshot.setId(WORKFLOW_ID);
        snapshot.setStatus(0);
        snapshot.setTitle("张三的年假申请");
        snapshot.setInitiator("zhangsan");
        snapshot.setTasks(List.of(task));
        return snapshot;
    }

    /**
     * 构造本地审批实例。
     *
     * @return 审批实例
     */
    private static ApprovalInstance localInstance() {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(1L);
        instance.setBusinessType("LEAVE");
        instance.setBusinessId(100L);
        instance.setWorkflowInstanceId(WORKFLOW_ID);
        instance.setTitle("张三的年假申请");
        instance.setInitiatorId(9L);
        instance.setInitiatorName("张三");
        instance.setBusinessUrl("/oa/leave/100");
        return instance;
    }
}
