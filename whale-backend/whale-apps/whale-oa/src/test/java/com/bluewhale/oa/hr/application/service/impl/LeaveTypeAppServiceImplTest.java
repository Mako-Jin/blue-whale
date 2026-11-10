package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.application.command.LeaveTypeSaveCommand;
import com.bluewhale.oa.hr.application.view.LeaveTypeView;
import com.bluewhale.oa.hr.domain.model.LeaveType;
import com.bluewhale.oa.hr.domain.model.LeaveTypeUnit;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LeaveTypeAppServiceImpl} 假期类型维护单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class LeaveTypeAppServiceImplTest {

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    private LeaveTypeAppServiceImpl leaveTypeAppService;

    /**
     * 构造被测服务。
     */
    @BeforeEach
    void setUp() {
        leaveTypeAppService = new LeaveTypeAppServiceImpl(leaveTypeRepository);
    }

    @Test
    @DisplayName("create：编码唯一时新增并默认启用")
    void shouldCreateType() {
        when(leaveTypeRepository.findByCode("COMPASSION")).thenReturn(Optional.empty());

        LeaveTypeView view = leaveTypeAppService.create(command("COMPASSION", "3.0"));

        ArgumentCaptor<LeaveType> captor = ArgumentCaptor.forClass(LeaveType.class);
        verify(leaveTypeRepository).insert(captor.capture());
        assertEquals("COMPASSION", captor.getValue().getCode());
        assertEquals(LeaveTypeUnit.DAY, captor.getValue().getUnit());
        assertTrue(captor.getValue().enabled());
        assertEquals(new BigDecimal("3.0"), view.getAnnualQuota());
    }

    @Test
    @DisplayName("create：编码重复时拒绝")
    void shouldRejectDuplicateCode() {
        when(leaveTypeRepository.findByCode("COMPASSION")).thenReturn(Optional.of(type(1L, "COMPASSION")));

        BizException exception = assertThrows(BizException.class,
                () -> leaveTypeAppService.create(command("COMPASSION", "3.0")));

        assertEquals(OaErrorCode.LEAVE_TYPE_EXISTS, exception.getCode());
        verify(leaveTypeRepository, never()).insert(any());
    }

    @Test
    @DisplayName("update：编码未被他人占用时可更新")
    void shouldUpdateType() {
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(type(1L, "ANNUAL")));
        when(leaveTypeRepository.findByCode("COMPASSION")).thenReturn(Optional.empty());

        leaveTypeAppService.update(1L, command("COMPASSION", "5.0"));

        ArgumentCaptor<LeaveType> captor = ArgumentCaptor.forClass(LeaveType.class);
        verify(leaveTypeRepository).update(captor.capture());
        assertEquals("COMPASSION", captor.getValue().getCode());
        assertEquals(new BigDecimal("5.0"), captor.getValue().getAnnualQuota());
    }

    @Test
    @DisplayName("toggle：按目标状态启停")
    void shouldToggleType() {
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(type(1L, "ANNUAL")));

        LeaveTypeView view = leaveTypeAppService.toggle(1L, false);

        assertFalse(view.getEnabled());
        verify(leaveTypeRepository).update(any());
    }

    @Test
    @DisplayName("listEnabled：仅返回启用类型视图")
    void shouldListEnabled() {
        when(leaveTypeRepository.listEnabled()).thenReturn(List.of(type(1L, "ANNUAL")));

        List<LeaveTypeView> views = leaveTypeAppService.listEnabled();

        assertEquals(1, views.size());
        assertEquals("ANNUAL", views.getFirst().getCode());
    }

    /**
     * 构造保存命令。
     *
     * @param code 编码
     * @param quota 年度额度
     * @return 保存命令
     */
    private static LeaveTypeSaveCommand command(String code, String quota) {
        LeaveTypeSaveCommand command = new LeaveTypeSaveCommand();
        command.setCode(code);
        command.setName("关怀假");
        command.setUnit("DAY");
        command.setAnnualQuota(new BigDecimal(quota));
        command.setMinUnit(new BigDecimal("0.5"));
        command.setRequiresAttachment(Boolean.FALSE);
        command.setAllowNegative(Boolean.FALSE);
        command.setSortNo(9);
        return command;
    }

    /**
     * 构造假期类型。
     *
     * @param id 主键
     * @param code 编码
     * @return 假期类型
     */
    private static LeaveType type(Long id, String code) {
        LeaveType leaveType = new LeaveType();
        leaveType.setId(id);
        leaveType.setCode(code);
        leaveType.setName("年假");
        leaveType.setUnit(LeaveTypeUnit.DAY);
        leaveType.setAnnualQuota(new BigDecimal("5.0"));
        leaveType.setMinUnit(new BigDecimal("0.5"));
        leaveType.setAllowNegative(Boolean.FALSE);
        leaveType.setRequiresAttachment(Boolean.FALSE);
        leaveType.setEnabled(Boolean.TRUE);
        leaveType.setSortNo(1);
        return leaveType;
    }
}
