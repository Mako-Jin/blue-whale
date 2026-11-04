package com.bluewhale.oa.approval.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.approval.application.command.ApprovalConfigSaveCommand;
import com.bluewhale.oa.approval.application.view.ApprovalConfigView;
import com.bluewhale.oa.approval.domain.model.ApprovalConfig;
import com.bluewhale.oa.approval.domain.repository.ApprovalConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalConfigAppServiceImpl} 审批业务配置单元测试。
 *
 * @author BlueWhale
 */
class ApprovalConfigAppServiceImplTest {

    private final ApprovalConfigRepository approvalConfigRepository = mock(ApprovalConfigRepository.class);

    private final ApprovalConfigAppServiceImpl approvalConfigAppService =
            new ApprovalConfigAppServiceImpl(approvalConfigRepository);

    @Test
    @DisplayName("create：业务类型重复时拒绝")
    void shouldRejectDuplicateBusinessType() {
        when(approvalConfigRepository.findByBusinessType("LEAVE"))
                .thenReturn(Optional.of(config(1L, true)));

        BizException exception = assertThrows(BizException.class,
                () -> approvalConfigAppService.create(command()));

        assertEquals(40901, exception.getCode());
        verify(approvalConfigRepository, never()).insert(any());
    }

    @Test
    @DisplayName("create：默认启用并落库")
    void shouldCreateEnabledConfig() {
        when(approvalConfigRepository.findByBusinessType("LEAVE")).thenReturn(Optional.empty());

        ApprovalConfigView view = approvalConfigAppService.create(command());

        ArgumentCaptor<ApprovalConfig> captor = ArgumentCaptor.forClass(ApprovalConfig.class);
        verify(approvalConfigRepository).insert(captor.capture());
        assertTrue(captor.getValue().isEnabled());
        assertEquals("OA_LEAVE", view.getDefinitionCode());
    }

    @Test
    @DisplayName("changeEnabled：停用配置后 requireEnabled 拒绝发起")
    void shouldRejectDisabledConfig() {
        when(approvalConfigRepository.findByBusinessType("LEAVE"))
                .thenReturn(Optional.of(config(1L, false)));

        BizException exception = assertThrows(BizException.class,
                () -> approvalConfigAppService.requireEnabled("LEAVE"));

        assertEquals(40401, exception.getCode());
    }

    @Test
    @DisplayName("changeEnabled：停用落库且视图状态为 false")
    void shouldDisableConfig() {
        when(approvalConfigRepository.findById(1L)).thenReturn(Optional.of(config(1L, true)));

        ApprovalConfigView view = approvalConfigAppService.changeEnabled(1L, false);

        assertFalse(view.getEnabled());
        verify(approvalConfigRepository).update(any());
    }

    @Test
    @DisplayName("update：改到已被占用的业务类型时拒绝")
    void shouldRejectBusinessTypeConflictOnUpdate() {
        when(approvalConfigRepository.findById(1L)).thenReturn(Optional.of(config(1L, true)));
        when(approvalConfigRepository.findByBusinessType("OVERTIME"))
                .thenReturn(Optional.of(config(2L, true)));

        ApprovalConfigSaveCommand command = command();
        command.setBusinessType("OVERTIME");
        BizException exception = assertThrows(BizException.class,
                () -> approvalConfigAppService.update(1L, command));

        assertEquals(40901, exception.getCode());
        verify(approvalConfigRepository, never()).update(any());
    }

    /**
     * 构造保存命令。
     *
     * @return 保存命令
     */
    private static ApprovalConfigSaveCommand command() {
        ApprovalConfigSaveCommand command = new ApprovalConfigSaveCommand();
        command.setBusinessType("LEAVE");
        command.setBusinessName("请假申请");
        command.setDefinitionCode("OA_LEAVE");
        command.setTitleTemplate("{applicant}的请假申请");
        command.setBusinessUrl("/oa/hr/leave");
        return command;
    }

    /**
     * 构造配置。
     *
     * @param id 主键
     * @param enabled 是否启用
     * @return 配置
     */
    private static ApprovalConfig config(Long id, boolean enabled) {
        ApprovalConfig config = new ApprovalConfig();
        config.setId(id);
        config.setBusinessType("LEAVE");
        config.setBusinessName("请假申请");
        config.setDefinitionCode("OA_LEAVE");
        config.setEnabled(enabled);
        return config;
    }
}
