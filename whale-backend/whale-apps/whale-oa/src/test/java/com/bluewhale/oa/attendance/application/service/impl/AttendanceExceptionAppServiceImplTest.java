package com.bluewhale.oa.attendance.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.domain.model.AttendanceException;
import com.bluewhale.oa.attendance.domain.model.AttendanceStatus;
import com.bluewhale.oa.attendance.domain.repository.AttendanceExceptionRepository;
import com.bluewhale.oa.common.OaErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AttendanceExceptionAppServiceImpl} 单元测试：考勤异常处理的校验分支与成功分支。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AttendanceExceptionAppServiceImplTest {

    @Mock
    private AttendanceExceptionRepository exceptionRepository;

    private AttendanceExceptionAppServiceImpl exceptionAppService;

    /**
     * 构造被测服务。
     */
    @BeforeEach
    void setUp() {
        exceptionAppService = new AttendanceExceptionAppServiceImpl(exceptionRepository);
    }

    @Test
    @DisplayName("handle：异常不存在时拒绝")
    void shouldRejectWhenExceptionNotFound() {
        when(exceptionRepository.findById(9L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> exceptionAppService.handle(9L, handleCommand("CONFIRMED"), "admin"));

        assertEquals(OaErrorCode.ATT_EXCEPTION_NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("handle：处理状态非 CONFIRMED/WAIVED 时拒绝")
    void shouldRejectInvalidHandleStatus() {
        when(exceptionRepository.findById(1L)).thenReturn(Optional.of(exception(AttendanceStatus.EXCEPTION_PENDING)));

        BizException ex = assertThrows(BizException.class,
                () -> exceptionAppService.handle(1L, handleCommand("RESOLVED"), "admin"));

        assertEquals(OaErrorCode.ATT_INVALID, ex.getCode());
        verify(exceptionRepository, never()).update(any());
    }

    @Test
    @DisplayName("handle：已处理的异常不能重复处理")
    void shouldRejectWhenAlreadyHandled() {
        when(exceptionRepository.findById(1L))
                .thenReturn(Optional.of(exception(AttendanceStatus.EXCEPTION_CONFIRMED)));

        BizException ex = assertThrows(BizException.class,
                () -> exceptionAppService.handle(1L, handleCommand("WAIVED"), "admin"));

        assertEquals(OaErrorCode.ATT_INVALID, ex.getCode());
        verify(exceptionRepository, never()).update(any());
    }

    @Test
    @DisplayName("handle：待处理异常确认成功，落处理人与处理时间")
    void shouldConfirmPendingException() {
        AttendanceException pending = exception(AttendanceStatus.EXCEPTION_PENDING);
        when(exceptionRepository.findById(1L)).thenReturn(Optional.of(pending));

        exceptionAppService.handle(1L, handleCommand("CONFIRMED"), "admin");

        ArgumentCaptor<AttendanceException> captor = ArgumentCaptor.forClass(AttendanceException.class);
        verify(exceptionRepository).update(captor.capture());
        AttendanceException saved = captor.getValue();
        assertEquals(AttendanceStatus.EXCEPTION_CONFIRMED, saved.getStatus());
        assertEquals("admin", saved.getHandler());
        assertNotNull(saved.getHandledTime());
        assertEquals("核实迟到", saved.getRemark());
    }

    @Test
    @DisplayName("handle：豁免分支同样落库")
    void shouldWaivePendingException() {
        AttendanceException pending = exception(AttendanceStatus.EXCEPTION_PENDING);
        when(exceptionRepository.findById(2L)).thenReturn(Optional.of(pending));

        exceptionAppService.handle(2L, handleCommand("waived"), "admin");

        ArgumentCaptor<AttendanceException> captor = ArgumentCaptor.forClass(AttendanceException.class);
        verify(exceptionRepository).update(captor.capture());
        assertEquals(AttendanceStatus.EXCEPTION_WAIVED, captor.getValue().getStatus());
    }

    /**
     * 构造异常处理命令。
     *
     * @param status 目标状态
     * @return 命令
     */
    private static AttendanceCommands.ExceptionHandleCommand handleCommand(String status) {
        AttendanceCommands.ExceptionHandleCommand command = new AttendanceCommands.ExceptionHandleCommand();
        command.setStatus(status);
        command.setRemark("核实迟到");
        return command;
    }

    /**
     * 构造考勤异常实体。
     *
     * @param status 状态
     * @return 异常实体
     */
    private static AttendanceException exception(String status) {
        AttendanceException exception = new AttendanceException();
        exception.setId(1L);
        exception.setUserId(100L);
        exception.setWorkDate(LocalDate.now());
        exception.setType(AttendanceStatus.EXCEPTION_LATE);
        exception.setStatus(status);
        return exception;
    }
}
