package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.hr.application.command.LeaveSaveCommand;
import com.bluewhale.oa.hr.application.command.LeaveStatusCommand;
import com.bluewhale.oa.hr.domain.model.Leave;
import com.bluewhale.oa.hr.domain.model.LeaveQuery;
import com.bluewhale.oa.hr.domain.model.LeaveStatus;
import com.bluewhale.oa.hr.domain.repository.LeaveRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LeaveAppServiceImpl} 请假申请状态机与校验单元测试。
 *
 * @author BlueWhale
 */
class LeaveAppServiceImplTest {

    private final LeaveRepository leaveRepository = mock(LeaveRepository.class);

    private final LeaveAppServiceImpl leaveAppService = new LeaveAppServiceImpl(leaveRepository);

    @Test
    @DisplayName("create：单号唯一时落库且初始状态 PENDING")
    void shouldCreatePendingLeave() {
        when(leaveRepository.existsByCode("OA-LV-202610-100", null)).thenReturn(false);

        leaveAppService.create(request());

        ArgumentCaptor<Leave> captor = ArgumentCaptor.forClass(Leave.class);
        verify(leaveRepository).insert(captor.capture());
        Leave saved = captor.getValue();
        assertEquals("OA-LV-202610-100", saved.getCode());
        assertEquals(LeaveStatus.PENDING, saved.getStatus());
        assertEquals(new BigDecimal("2.0"), saved.getDays());
    }

    @Test
    @DisplayName("create：单号重复时拒绝")
    void shouldRejectDuplicateCode() {
        when(leaveRepository.existsByCode("OA-LV-202610-100", null)).thenReturn(true);

        BizException exception = assertThrows(BizException.class, () -> leaveAppService.create(request()));

        assertEquals(400, exception.getCode());
        verify(leaveRepository, never()).insert(any());
    }

    @Test
    @DisplayName("create：结束日期早于开始日期时拒绝")
    void shouldRejectInvalidDateRange() {
        LeaveSaveCommand command = request();
        command.setEndDate(LocalDate.of(2026, 10, 20));

        BizException exception = assertThrows(BizException.class, () -> leaveAppService.create(command));

        assertEquals(400, exception.getCode());
        verify(leaveRepository, never()).existsByCode(any(), any());
    }

    @Test
    @DisplayName("updateStatus：PENDING 可通过审批")
    void shouldApprovePendingLeave() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(pendingLeave()));

        LeaveStatusCommand command = new LeaveStatusCommand();
        command.setStatus("APPROVED");
        leaveAppService.updateStatus(1L, command);

        ArgumentCaptor<Leave> captor = ArgumentCaptor.forClass(Leave.class);
        verify(leaveRepository).update(captor.capture());
        assertEquals(LeaveStatus.APPROVED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("updateStatus：终态不可再次变更")
    void shouldRejectTerminalStatusChange() {
        Leave leave = pendingLeave();
        leave.setStatus(LeaveStatus.APPROVED);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        LeaveStatusCommand command = new LeaveStatusCommand();
        command.setStatus("REJECTED");
        BizException exception = assertThrows(BizException.class,
                () -> leaveAppService.updateStatus(1L, command));

        assertEquals(400, exception.getCode());
        verify(leaveRepository, never()).update(any());
    }

    @Test
    @DisplayName("page：透传分页参数并映射视图对象")
    void shouldPageLeaves() {
        LeaveQuery query = new LeaveQuery();
        when(leaveRepository.page(query)).thenReturn(List.of(pendingLeave()));
        when(leaveRepository.count(query)).thenReturn(1L);

        var result = leaveAppService.page(query);

        assertEquals(1L, result.getTotal());
        assertEquals("OA-LV-202610-100", result.getRecords().getFirst().getCode());
    }

    /**
     * 构造新增命令。
     *
     * @return 新增命令
     */
    private static LeaveSaveCommand request() {
        LeaveSaveCommand command = new LeaveSaveCommand();
        command.setCode("OA-LV-202610-100");
        command.setApplicant("admin");
        command.setLeaveType("ANNUAL");
        command.setStartDate(LocalDate.of(2026, 10, 26));
        command.setEndDate(LocalDate.of(2026, 10, 27));
        command.setDays(new BigDecimal("2.0"));
        command.setReason("年假");
        return command;
    }

    /**
     * 构造待审批申请。
     *
     * @return 申请领域模型
     */
    private static Leave pendingLeave() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setCode("OA-LV-202610-100");
        leave.setApplicant("admin");
        leave.setLeaveType("ANNUAL");
        leave.setStartDate(LocalDate.of(2026, 10, 26));
        leave.setEndDate(LocalDate.of(2026, 10, 27));
        leave.setDays(new BigDecimal("2.0"));
        leave.setStatus(LeaveStatus.PENDING);
        return leave;
    }
}
