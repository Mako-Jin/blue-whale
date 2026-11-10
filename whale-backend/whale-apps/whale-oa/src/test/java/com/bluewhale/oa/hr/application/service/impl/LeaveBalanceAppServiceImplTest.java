package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.application.command.LeaveBalanceAdjustCommand;
import com.bluewhale.oa.hr.application.view.LeaveBalanceView;
import com.bluewhale.oa.hr.domain.model.LeaveBalance;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceAction;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceLog;
import com.bluewhale.oa.hr.domain.model.LeaveType;
import com.bluewhale.oa.hr.domain.model.LeaveTypeUnit;
import com.bluewhale.oa.hr.domain.repository.LeaveBalanceLogRepository;
import com.bluewhale.oa.hr.domain.repository.LeaveBalanceRepository;
import com.bluewhale.oa.hr.domain.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LeaveBalanceAppServiceImpl} 额度初始化 / 冻结 / 扣减 / 调整单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class LeaveBalanceAppServiceImplTest {

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private LeaveBalanceLogRepository leaveBalanceLogRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    private LeaveBalanceAppServiceImpl leaveBalanceAppService;

    /**
     * 构造被测服务。
     */
    @BeforeEach
    void setUp() {
        leaveBalanceAppService = new LeaveBalanceAppServiceImpl(leaveBalanceRepository,
                leaveBalanceLogRepository, leaveTypeRepository);
    }

    @Test
    @DisplayName("initForUser：按启用类型年度额度建账并写 INIT 流水")
    void shouldInitBalancesAndWriteLog() {
        when(leaveTypeRepository.listEnabled()).thenReturn(List.of(type(1L, "5.0")));
        when(leaveBalanceRepository.findByUserAndTypeAndYear(100L, 1L, 2026)).thenReturn(Optional.empty());
        when(leaveBalanceRepository.listByUserAndYear(100L, 2026)).thenReturn(List.of());

        leaveBalanceAppService.initForUser(100L, 2026);

        ArgumentCaptor<LeaveBalance> balanceCaptor = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(leaveBalanceRepository).insert(balanceCaptor.capture());
        assertEquals(new BigDecimal("5.0"), balanceCaptor.getValue().getTotalDays());
        ArgumentCaptor<LeaveBalanceLog> logCaptor = ArgumentCaptor.forClass(LeaveBalanceLog.class);
        verify(leaveBalanceLogRepository).insert(logCaptor.capture());
        assertEquals(LeaveBalanceAction.INIT, logCaptor.getValue().getAction());
    }

    @Test
    @DisplayName("initForUser：已有额度时跳过且不重复写流水")
    void shouldSkipExistingBalance() {
        when(leaveTypeRepository.listEnabled()).thenReturn(List.of(type(1L, "5.0")));
        when(leaveBalanceRepository.findByUserAndTypeAndYear(100L, 1L, 2026))
                .thenReturn(Optional.of(balance()));
        when(leaveBalanceRepository.listByUserAndYear(100L, 2026)).thenReturn(List.of(balance()));

        leaveBalanceAppService.initForUser(100L, 2026);

        verify(leaveBalanceRepository, never()).insert(any());
        verify(leaveBalanceLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("freeze：冻结额度并写 FREEZE 流水")
    void shouldFreezeAndWriteLog() {
        when(leaveBalanceRepository.findByUserAndTypeAndYear(100L, 1L, 2026))
                .thenReturn(Optional.of(balance()));

        leaveBalanceAppService.freeze(100L, 1L, 2026, new BigDecimal("2.0"), false, "LEAVE", 1L);

        verify(leaveBalanceRepository).update(any());
        ArgumentCaptor<LeaveBalanceLog> captor = ArgumentCaptor.forClass(LeaveBalanceLog.class);
        verify(leaveBalanceLogRepository).insert(captor.capture());
        assertEquals(LeaveBalanceAction.FREEZE, captor.getValue().getAction());
        assertEquals(new BigDecimal("2.0"), captor.getValue().getAfterFrozen());
    }

    @Test
    @DisplayName("deduct：先释放冻结再累加已使用并写 DEDUCT 流水")
    void shouldDeductAndWriteLog() {
        LeaveBalance balance = balance();
        balance.setFrozenDays(new BigDecimal("2.0"));
        when(leaveBalanceRepository.findByUserAndTypeAndYear(100L, 1L, 2026))
                .thenReturn(Optional.of(balance));

        leaveBalanceAppService.deduct(100L, 1L, 2026, new BigDecimal("2.0"), "LEAVE", 1L);

        assertEquals(0, balance.getFrozenDays().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("2.0"), balance.getUsedDays());
        ArgumentCaptor<LeaveBalanceLog> captor = ArgumentCaptor.forClass(LeaveBalanceLog.class);
        verify(leaveBalanceLogRepository).insert(captor.capture());
        assertEquals(LeaveBalanceAction.DEDUCT, captor.getValue().getAction());
    }

    @Test
    @DisplayName("freeze：额度不存在时提示先初始化")
    void shouldThrowWhenBalanceMissing() {
        when(leaveBalanceRepository.findByUserAndTypeAndYear(100L, 1L, 2026)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> leaveBalanceAppService.freeze(100L, 1L, 2026, new BigDecimal("2.0"), false, "LEAVE", 1L));

        assertEquals(OaErrorCode.LEAVE_BALANCE_NOT_FOUND, exception.getCode());
        verify(leaveBalanceRepository, never()).update(any());
    }

    @Test
    @DisplayName("adjust：调整总额度并写 ADJUST 流水")
    void shouldAdjustTotal() {
        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(balance()));

        LeaveBalanceAdjustCommand command = new LeaveBalanceAdjustCommand();
        command.setTotalDays(new BigDecimal("8.0"));
        command.setRemark("补充年假");
        LeaveBalanceView view = leaveBalanceAppService.adjust(1L, command);

        assertEquals(new BigDecimal("8.0"), view.getTotalDays());
        assertEquals(new BigDecimal("8.0"), view.getAvailableDays());
        ArgumentCaptor<LeaveBalanceLog> captor = ArgumentCaptor.forClass(LeaveBalanceLog.class);
        verify(leaveBalanceLogRepository, times(1)).insert(captor.capture());
        assertEquals(LeaveBalanceAction.ADJUST, captor.getValue().getAction());
    }

    /**
     * 构造额度。
     *
     * @return 额度领域模型
     */
    private static LeaveBalance balance() {
        LeaveBalance balance = new LeaveBalance();
        balance.setId(1L);
        balance.setUserId(100L);
        balance.setLeaveTypeId(1L);
        balance.setBalanceYear(2026);
        balance.setTotalDays(new BigDecimal("5.0"));
        balance.setUsedDays(BigDecimal.ZERO);
        balance.setFrozenDays(BigDecimal.ZERO);
        return balance;
    }

    /**
     * 构造假期类型。
     *
     * @param id 主键
     * @param quota 年度额度
     * @return 假期类型
     */
    private static LeaveType type(Long id, String quota) {
        LeaveType leaveType = new LeaveType();
        leaveType.setId(id);
        leaveType.setCode("ANNUAL");
        leaveType.setName("年假");
        leaveType.setUnit(LeaveTypeUnit.DAY);
        leaveType.setAnnualQuota(new BigDecimal(quota));
        leaveType.setMinUnit(new BigDecimal("0.5"));
        leaveType.setAllowNegative(Boolean.FALSE);
        leaveType.setRequiresAttachment(Boolean.FALSE);
        leaveType.setEnabled(Boolean.TRUE);
        leaveType.setSortNo(1);
        return leaveType;
    }
}
