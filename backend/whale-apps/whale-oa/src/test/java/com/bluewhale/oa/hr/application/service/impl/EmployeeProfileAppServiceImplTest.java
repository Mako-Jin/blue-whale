package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.application.command.EmployeeProfileSaveCommand;
import com.bluewhale.oa.hr.application.view.EmployeeProfileView;
import com.bluewhale.oa.hr.domain.model.EmployeeProfile;
import com.bluewhale.oa.hr.domain.model.EmploymentStatus;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EmployeeProfileAppServiceImpl} 员工档案幂等 upsert 单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class EmployeeProfileAppServiceImplTest {

    @Mock
    private EmployeeProfileRepository employeeProfileRepository;

    private EmployeeProfileAppServiceImpl employeeProfileAppService;

    /**
     * 构造被测服务。
     */
    @BeforeEach
    void setUp() {
        employeeProfileAppService = new EmployeeProfileAppServiceImpl(employeeProfileRepository);
    }

    @Test
    @DisplayName("save：档案不存在时新增，默认在职且公开")
    void shouldCreateProfileWhenAbsent() {
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.empty());

        EmployeeProfileView view = employeeProfileAppService.save(command());

        ArgumentCaptor<EmployeeProfile> captor = ArgumentCaptor.forClass(EmployeeProfile.class);
        verify(employeeProfileRepository).insert(captor.capture());
        assertEquals(100L, captor.getValue().getUserId());
        assertEquals(EmploymentStatus.ACTIVE, captor.getValue().getEmploymentStatus());
        assertEquals(Boolean.TRUE, captor.getValue().getCultureVisible());
        assertEquals("E100", view.getEmployeeNo());
        verify(employeeProfileRepository, never()).update(any());
    }

    @Test
    @DisplayName("save：档案已存在时更新，不重复新增")
    void shouldUpdateProfileWhenExists() {
        EmployeeProfile existing = new EmployeeProfile();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setEmploymentStatus(EmploymentStatus.PROBATION);
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(existing));

        EmployeeProfileSaveCommand command = command();
        command.setEmploymentStatus("REGULAR");
        EmployeeProfileView view = employeeProfileAppService.save(command);

        ArgumentCaptor<EmployeeProfile> captor = ArgumentCaptor.forClass(EmployeeProfile.class);
        verify(employeeProfileRepository).update(captor.capture());
        assertEquals(10L, captor.getValue().getId());
        assertEquals(EmploymentStatus.REGULAR, captor.getValue().getEmploymentStatus());
        assertEquals(10L, view.getId());
        verify(employeeProfileRepository, never()).insert(any());
    }

    @Test
    @DisplayName("findByUserId：档案不存在抛业务异常")
    void shouldThrowWhenProfileMissing() {
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> employeeProfileAppService.findByUserId(100L));

        assertEquals(OaErrorCode.EMPLOYEE_PROFILE_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("delete：按用户ID 定位后逻辑删除")
    void shouldDeleteByUserId() {
        EmployeeProfile existing = new EmployeeProfile();
        existing.setId(10L);
        existing.setUserId(100L);
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(existing));

        employeeProfileAppService.delete(100L);

        verify(employeeProfileRepository).deleteById(10L);
    }

    /**
     * 构造保存命令。
     *
     * @return 保存命令
     */
    private static EmployeeProfileSaveCommand command() {
        EmployeeProfileSaveCommand command = new EmployeeProfileSaveCommand();
        command.setUserId(100L);
        command.setEmployeeNo("E100");
        command.setDeptId(20L);
        command.setHireDate(LocalDate.of(2024, 3, 1));
        return command;
    }
}
