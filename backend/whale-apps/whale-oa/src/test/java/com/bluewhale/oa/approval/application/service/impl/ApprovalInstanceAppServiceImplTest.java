package com.bluewhale.oa.approval.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.approval.application.command.ApprovalInstanceRegisterCommand;
import com.bluewhale.oa.approval.application.command.ApprovalResultCommand;
import com.bluewhale.oa.approval.application.command.ApprovalRouteCommand;
import com.bluewhale.oa.approval.application.service.ApprovalConfigAppService;
import com.bluewhale.oa.approval.application.view.ApprovalConfigView;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.approval.domain.model.ApprovalInstance;
import com.bluewhale.oa.approval.domain.model.ApprovalStatus;
import com.bluewhale.oa.approval.domain.repository.ApprovalEffectRepository;
import com.bluewhale.oa.approval.domain.repository.ApprovalInstanceRepository;
import com.bluewhale.oa.approval.domain.repository.ApprovalRouteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalInstanceAppServiceImpl} 审批实例状态机与幂等单元测试。
 *
 * @author BlueWhale
 */
class ApprovalInstanceAppServiceImplTest {

    private final ApprovalInstanceRepository approvalInstanceRepository = mock(ApprovalInstanceRepository.class);

    private final ApprovalRouteRepository approvalRouteRepository = mock(ApprovalRouteRepository.class);

    private final ApprovalEffectRepository approvalEffectRepository = mock(ApprovalEffectRepository.class);

    private final ApprovalConfigAppService approvalConfigAppService = mock(ApprovalConfigAppService.class);

    private final ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);

    private final ApprovalInstanceAppServiceImpl approvalInstanceAppService =
            new ApprovalInstanceAppServiceImpl(approvalInstanceRepository, approvalRouteRepository,
                    approvalEffectRepository, approvalConfigAppService, applicationEventPublisher);

    @Test
    @DisplayName("register：落审批实例与审批人快照，标题按模板渲染")
    void shouldRegisterInstanceWithRouteSnapshot() {
        when(approvalInstanceRepository.findByBusiness("LEAVE", 11L)).thenReturn(Optional.empty());
        when(approvalConfigAppService.requireEnabled("LEAVE")).thenReturn(configWithTemplate());
        when(approvalInstanceRepository.insertIfAbsent(any())).thenAnswer(invocation -> {
            ApprovalInstance instance = invocation.getArgument(0);
            instance.setId(99L);
            return true;
        });

        ApprovalInstanceView view = approvalInstanceAppService.register(registerCommand());

        assertNotNull(view);
        assertEquals(99L, view.getId());
        assertEquals("RUNNING", view.getStatus());
        assertEquals("OA_LEAVE", view.getDefinitionCode());
        assertEquals("张三的请假申请", view.getTitle());
        assertEquals("dept_leader", view.getCurrentNodeKey());
        assertEquals("leader_a,leader_b", view.getCurrentAssignees());
        verify(approvalRouteRepository).insertAll(any());
    }

    @Test
    @DisplayName("register：同一业务重复登记返回既有实例，不重复写库")
    void shouldReturnExistingInstanceOnDuplicateRegister() {
        ApprovalInstance existing = runningInstance(7L);
        when(approvalInstanceRepository.findByBusiness("LEAVE", 11L)).thenReturn(Optional.of(existing));

        ApprovalInstanceView view = approvalInstanceAppService.register(registerCommand());

        assertEquals(7L, view.getId());
        verify(approvalInstanceRepository, never()).insertIfAbsent(any());
        verify(approvalConfigAppService, never()).requireEnabled(any());
    }

    @Test
    @DisplayName("syncResult：RUNNING 迁移到 APPROVED 并落库")
    void shouldSyncResultOnce() {
        ApprovalInstance instance = runningInstance(7L);
        when(approvalInstanceRepository.findById(7L)).thenReturn(Optional.of(instance));

        ApprovalResultCommand command = new ApprovalResultCommand();
        command.setStatus("APPROVED");
        ApprovalInstanceView view = approvalInstanceAppService.syncResult(7L, command);

        assertEquals("APPROVED", view.getStatus());
        assertNotNull(view.getFinishedAt());
        verify(approvalInstanceRepository).update(instance);
    }

    @Test
    @DisplayName("syncResult：重复同步同一终态不重复落库（副作用幂等前提）")
    void shouldIgnoreDuplicateResultSync() {
        ApprovalInstance instance = runningInstance(7L);
        instance.setStatus(ApprovalStatus.APPROVED);
        when(approvalInstanceRepository.findById(7L)).thenReturn(Optional.of(instance));

        ApprovalResultCommand command = new ApprovalResultCommand();
        command.setStatus("APPROVED");
        approvalInstanceAppService.syncResult(7L, command);

        verify(approvalInstanceRepository, never()).update(any());
    }

    @Test
    @DisplayName("syncResult：终态不可迁移到其他终态")
    void shouldRejectConflictingResult() {
        ApprovalInstance instance = runningInstance(7L);
        instance.setStatus(ApprovalStatus.APPROVED);
        when(approvalInstanceRepository.findById(7L)).thenReturn(Optional.of(instance));

        ApprovalResultCommand command = new ApprovalResultCommand();
        command.setStatus("REJECTED");
        BizException exception = assertThrows(BizException.class,
                () -> approvalInstanceAppService.syncResult(7L, command));

        assertEquals(40902, exception.getCode());
        verify(approvalInstanceRepository, never()).update(any());
    }

    @Test
    @DisplayName("attachWorkflow：已绑定 Workflow 实例时幂等且不重复更新")
    void shouldAttachWorkflowIdempotently() {
        ApprovalInstance instance = runningInstance(7L);
        instance.setWorkflowInstanceId(100L);
        when(approvalInstanceRepository.findById(7L)).thenReturn(Optional.of(instance));

        ApprovalInstanceView view = approvalInstanceAppService.attachWorkflow(7L, 100L);

        assertEquals(100L, view.getWorkflowInstanceId());
        verify(approvalInstanceRepository, never()).update(any());
    }

    @Test
    @DisplayName("detail：实例不存在返回 40402")
    void shouldRejectMissingInstance() {
        when(approvalInstanceRepository.findById(anyLong())).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> approvalInstanceAppService.detail(404L));

        assertEquals(40402, exception.getCode());
    }

    /**
     * 构造登记命令。
     *
     * @return 登记命令
     */
    private static ApprovalInstanceRegisterCommand registerCommand() {
        ApprovalInstanceRegisterCommand command = new ApprovalInstanceRegisterCommand();
        command.setBusinessType("LEAVE");
        command.setBusinessId(11L);
        command.setBusinessCode("OA-LV-202611-001");
        command.setInitiatorId(3L);
        command.setInitiatorName("张三");
        command.setRoutes(List.of(route("dept_leader", "部门负责人", "leader_a"),
                route("dept_leader", "部门负责人", "leader_b")));
        return command;
    }

    /**
     * 构造审批人命令。
     *
     * @param nodeKey 节点标识
     * @param nodeName 节点名称
     * @param account 审批人账号
     * @return 审批人命令
     */
    private static ApprovalRouteCommand route(String nodeKey, String nodeName, String account) {
        ApprovalRouteCommand command = new ApprovalRouteCommand();
        command.setNodeKey(nodeKey);
        command.setNodeName(nodeName);
        command.setAssigneeAccount(account);
        command.setResolverType("DEPT_LEADER");
        return command;
    }

    /**
     * 构造审批配置视图。
     *
     * @return 配置视图
     */
    private static ApprovalConfigView configWithTemplate() {
        ApprovalConfigView config = new ApprovalConfigView();
        config.setBusinessType("LEAVE");
        config.setBusinessName("请假申请");
        config.setDefinitionCode("OA_LEAVE");
        config.setTitleTemplate("{applicant}的请假申请");
        config.setBusinessUrl("/oa/hr/leave");
        config.setEnabled(true);
        return config;
    }

    /**
     * 构造审批中实例。
     *
     * @param id 主键
     * @return 审批实例
     */
    private static ApprovalInstance runningInstance(Long id) {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(id);
        instance.setBusinessType("LEAVE");
        instance.setBusinessId(11L);
        instance.setDefinitionCode("OA_LEAVE");
        instance.setTitle("请假申请");
        instance.setStatus(ApprovalStatus.RUNNING);
        return instance;
    }
}
