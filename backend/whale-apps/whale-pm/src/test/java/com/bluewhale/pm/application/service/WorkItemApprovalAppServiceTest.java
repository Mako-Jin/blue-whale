package com.bluewhale.pm.application.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.infrastructure.client.WorkflowApprovalClient;
import com.bluewhale.pm.infrastructure.config.PmApprovalProperties;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link WorkItemApprovalAppService} 单元测试：开关校验、参数校验、实例发起与动态留痕。
 *
 * @author BlueWhale
 */
class WorkItemApprovalAppServiceTest {

    private final WorkItemGuard workItemGuard = mock(WorkItemGuard.class);

    private final ProjectGuard projectGuard = mock(ProjectGuard.class);

    private final WorkItemActivityAppService activityAppService = mock(WorkItemActivityAppService.class);

    private final WorkflowApprovalClient approvalClient = mock(WorkflowApprovalClient.class);

    private final PmApprovalProperties properties = new PmApprovalProperties();

    private final WorkItemApprovalAppService service = new WorkItemApprovalAppService(workItemGuard,
            projectGuard, activityAppService, approvalClient, properties);

    /**
     * 构造工作项。
     *
     * @param title 标题
     * @return 工作项实体
     */
    private static PmWorkItem item(String title) {
        PmWorkItem item = new PmWorkItem();
        item.setId(101L);
        item.setProjectId(3L);
        item.setItemKey("PM-1");
        item.setTitle(title);
        return item;
    }

    @Test
    @DisplayName("默认关闭：直接拒绝且不发起外部调用")
    void disabledShouldReject() {
        BizException error = assertThrows(BizException.class,
                () -> service.submit(101L, 1L, "admin", "sys:pm:item:approval"));

        assertEquals("审批功能未开启", error.getMessage());
        verifyNoInteractions(approvalClient);
    }

    @Test
    @DisplayName("开启但缺少流程定义：拒绝并发提示")
    void missingDefinitionShouldReject() {
        properties.setEnabled(true);

        BizException error = assertThrows(BizException.class,
                () -> service.submit(101L, 1L, "admin", "sys:pm:item:approval"));

        assertEquals("未配置审批流程定义", error.getMessage());
        verifyNoInteractions(approvalClient);
    }

    @Test
    @DisplayName("发起审批：businessKey 为工作项ID，并记录审批动态")
    void submitShouldStartInstance() {
        properties.setEnabled(true);
        properties.setDefinitionId(77L);
        when(workItemGuard.requireWritable(101L, 1L)).thenReturn(item("修复登录缺陷"));
        when(projectGuard.requireActiveProject(3L)).thenReturn(new PmProject());
        when(approvalClient.start(anyLong(), anyString(), anyString(), anyMap(), anyLong(), anyString(),
                anyString())).thenReturn("inst-1");

        String instanceId = service.submit(101L, 1L, "admin", "sys:pm:item:approval");

        assertEquals("inst-1", instanceId);
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(approvalClient).start(eq(77L), eq("[PM-1] 修复登录缺陷"), eq("101"), variables.capture(),
                eq(1L), eq("admin"), eq("sys:pm:item:approval"));
        assertEquals(3L, variables.getValue().get("projectId"));
        verify(activityAppService).record(eq(3L), eq(101L), eq(1L), eq(ActivityAction.UPDATE),
                eq("approval"), isNull(), eq("inst-1"));
    }

    @Test
    @DisplayName("标题超长：截断到 120 字符以内")
    void titleShouldBeTruncated() {
        properties.setEnabled(true);
        properties.setDefinitionId(77L);
        when(workItemGuard.requireWritable(101L, 1L)).thenReturn(item("缺".repeat(200)));
        when(projectGuard.requireActiveProject(3L)).thenReturn(new PmProject());
        when(approvalClient.start(anyLong(), anyString(), anyString(), anyMap(), anyLong(), anyString(),
                anyString())).thenReturn("inst-2");

        service.submit(101L, 1L, "admin", null);

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(approvalClient).start(eq(77L), title.capture(), eq("101"), anyMap(), eq(1L), eq("admin"),
                any());
        assertTrue(title.getValue().length() <= 120);
    }

    @Test
    @DisplayName("非项目成员写入：由守卫抛出，不发起审批")
    void unauthorizedShouldPropagate() {
        properties.setEnabled(true);
        properties.setDefinitionId(77L);
        when(workItemGuard.requireWritable(101L, 1L))
                .thenThrow(new BizException(com.bluewhale.common.core.exception.ErrorCode.PARAM_ERROR, "无权限"));

        assertThrows(BizException.class, () -> service.submit(101L, 1L, "admin", null));
        verify(approvalClient, never()).start(anyLong(), anyString(), anyString(), anyMap(), anyLong(),
                anyString(), anyString());
    }
}
