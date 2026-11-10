package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.SequenceAppService;
import com.bluewhale.oa.hr.application.command.EmployeeChangeCommands;
import com.bluewhale.oa.hr.application.view.EmployeeChangeViews;
import com.bluewhale.oa.hr.domain.gateway.EmployeeLifecycleGateway;
import com.bluewhale.oa.hr.domain.gateway.LifecycleResult;
import com.bluewhale.oa.hr.domain.model.EmployeeChange;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeStatus;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeType;
import com.bluewhale.oa.hr.domain.model.EmployeeProfile;
import com.bluewhale.oa.hr.domain.model.EmploymentStatus;
import com.bluewhale.oa.hr.domain.repository.EmployeeChangeRepository;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EmployeeChangeAppServiceImpl} 人员异动状态机与执行幂等测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class EmployeeChangeAppServiceImplTest {

    @Mock
    private EmployeeChangeRepository employeeChangeRepository;

    @Mock
    private EmployeeProfileRepository employeeProfileRepository;

    @Mock
    private EmployeeLifecycleGateway employeeLifecycleGateway;

    @Mock
    private ApprovalInstanceAppService approvalInstanceAppService;

    @Mock
    private SequenceAppService sequenceAppService;

    private EmployeeChangeAppServiceImpl employeeChangeAppService;

    @BeforeEach
    void setUp() {
        employeeChangeAppService = new EmployeeChangeAppServiceImpl(employeeChangeRepository,
                employeeProfileRepository, employeeLifecycleGateway, approvalInstanceAppService,
                sequenceAppService);
    }

    @Test
    @DisplayName("create：离职异动自动生成 4 条清单任务")
    void shouldCreateLeaveChecklistTasks() {
        when(sequenceAppService.nextCode("HR_CHANGE", "OA-HC")).thenReturn("OA-HC-20261107-001");
        doAnswer(invocation -> {
            EmployeeChange change = invocation.getArgument(0);
            change.setId(1L);
            return null;
        }).when(employeeChangeRepository).insert(any(EmployeeChange.class));
        when(employeeChangeRepository.findTasks(1L)).thenReturn(List.of());
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.empty());

        EmployeeChangeViews.Detail detail = employeeChangeAppService.create(command(EmployeeChangeType.LEAVE),
                10L, "张三");

        assertEquals("OA-HC-20261107-001", detail.getChangeNo());
        assertEquals(EmployeeChangeStatus.DRAFT.name(), detail.getStatus());
        assertEquals("张三", detail.getApplicantName());
        verify(employeeChangeRepository, times(4)).insertTask(any());
    }

    @Test
    @DisplayName("submit：登记审批实例并流转为审批中")
    void shouldSubmitApproval() {
        EmployeeChange change = draft();
        when(employeeChangeRepository.findById(1L)).thenReturn(change);
        ApprovalInstanceView instance = new ApprovalInstanceView();
        instance.setId(77L);
        when(approvalInstanceAppService.register(any())).thenReturn(instance);
        when(employeeChangeRepository.findTasks(1L)).thenReturn(List.of());

        EmployeeChangeViews.Detail detail = employeeChangeAppService.submit(1L);

        assertEquals(EmployeeChangeStatus.SUBMITTED.name(), detail.getStatus());
        assertEquals(77L, detail.getApprovalInstanceId());
        verify(employeeChangeRepository).update(change);
    }

    @Test
    @DisplayName("syncApprovalResult：审批通过执行一次，重复事件幂等")
    void shouldExecuteOnceOnApproved() {
        EmployeeChange change = submitted();
        when(employeeChangeRepository.findById(1L)).thenReturn(change);
        EmployeeProfile profile = new EmployeeProfile();
        profile.setId(9L);
        profile.setUserId(100L);
        profile.setEmploymentStatus(EmploymentStatus.ACTIVE);
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile));
        when(employeeLifecycleGateway.apply(change)).thenReturn(new LifecycleResult(100L, 1, null));

        employeeChangeAppService.syncApprovalResult(1L, "APPROVED");
        employeeChangeAppService.syncApprovalResult(1L, "APPROVED");

        assertEquals(EmployeeChangeStatus.EXECUTED, change.getStatus());
        verify(employeeLifecycleGateway, times(1)).apply(change);
        verify(employeeProfileRepository, times(1)).update(any(EmployeeProfile.class));
    }

    @Test
    @DisplayName("syncApprovalResult：审批驳回流转为已驳回且不调用 Auth")
    void shouldRejectWithoutExecution() {
        EmployeeChange change = submitted();
        when(employeeChangeRepository.findById(1L)).thenReturn(change);

        employeeChangeAppService.syncApprovalResult(1L, "REJECTED");

        assertEquals(EmployeeChangeStatus.REJECTED, change.getStatus());
        verify(employeeLifecycleGateway, never()).apply(any());
    }

    @Test
    @DisplayName("execute：申请人不能执行自己提交的异动（四眼原则）")
    void shouldRejectSelfExecution() {
        EmployeeChange change = approved();
        when(employeeChangeRepository.findById(1L)).thenReturn(change);

        BizException exception = assertThrows(BizException.class,
                () -> employeeChangeAppService.execute(1L, 10L, "张三"));

        assertEquals(OaErrorCode.HR_CHANGE_FOUR_EYES, exception.getCode());
        verify(employeeLifecycleGateway, never()).apply(any());
    }

    /**
     * 构造保存命令。
     *
     * @param type 异动类型
     * @return 命令
     */
    private static EmployeeChangeCommands.Save command(EmployeeChangeType type) {
        EmployeeChangeCommands.Save command = new EmployeeChangeCommands.Save();
        command.setChangeType(type.name());
        command.setUserId(100L);
        command.setEmployeeName("李四");
        command.setEffectiveDate(LocalDate.of(2026, 11, 10));
        command.setReason("个人原因");
        return command;
    }

    /**
     * 构造草稿异动单。
     *
     * @return 草稿
     */
    private static EmployeeChange draft() {
        EmployeeChange change = new EmployeeChange();
        change.setId(1L);
        change.setChangeNo("OA-HC-1");
        change.setChangeType(EmployeeChangeType.TRANSFER);
        change.setUserId(100L);
        change.setEmployeeName("李四");
        change.setEffectiveDate(LocalDate.of(2026, 11, 10));
        change.setApplicantId(10L);
        change.setApplicantName("张三");
        change.setFromDeptId(20L);
        change.setToDeptId(30L);
        change.setToStatus(EmploymentStatus.ACTIVE.name());
        change.setStatus(EmployeeChangeStatus.DRAFT);
        return change;
    }

    /**
     * 构造审批中异动单。
     *
     * @return 审批中
     */
    private static EmployeeChange submitted() {
        EmployeeChange change = draft();
        change.setStatus(EmployeeChangeStatus.SUBMITTED);
        change.setApprovalInstanceId(77L);
        return change;
    }

    /**
     * 构造审批通过异动单。
     *
     * @return 待执行
     */
    private static EmployeeChange approved() {
        EmployeeChange change = draft();
        change.setStatus(EmployeeChangeStatus.APPROVED);
        return change;
    }
}
