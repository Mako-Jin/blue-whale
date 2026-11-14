package com.bluewhale.workflow.scheduler;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.workflow.config.WorkflowTimeoutProperties;
import com.bluewhale.workflow.mapper.OverdueTaskRow;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.support.WorkflowMessageClient;
import com.bluewhale.workflow.support.WorkflowMetrics;
import com.bluewhale.workflow.support.WorkflowUserDirectoryClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TaskTimeoutScheduler} 超时扫描 / 提醒开关 / 指标测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TaskTimeoutSchedulerTest {

    @Mock
    private ProcessTaskMapper processTaskMapper;

    @Mock
    private WorkflowUserDirectoryClient userDirectoryClient;

    @Mock
    private WorkflowMessageClient messageClient;

    private SimpleMeterRegistry registry;

    private WorkflowTimeoutProperties properties;

    private TaskTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        properties = new WorkflowTimeoutProperties();
        scheduler = new TaskTimeoutScheduler(processTaskMapper, new WorkflowMetrics(registry),
                properties, userDirectoryClient, messageClient);
    }

    @Test
    @DisplayName("无超时待办：返回 overdue=0 且不记录指标")
    void shouldReturnZeroWhenNoOverdue() {
        when(processTaskMapper.selectOverdue(any(LocalDateTime.class), anyInt())).thenReturn(List.of());

        assertEquals("overdue=0", scheduler.scan());
        assertNull(registry.find("workflow.task.overdue").counter());
    }

    @Test
    @DisplayName("提醒关闭：只记指标，不解析账号也不发消息")
    void shouldOnlyRecordMetricWhenNotifyDisabled() {
        when(processTaskMapper.selectOverdue(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(row(1L, 9L, "manager")));

        String result = scheduler.scan();

        assertTrue(result.contains("notify=disabled"));
        assertEquals(1.0, registry.find("workflow.task.overdue").counter().count());
        verify(userDirectoryClient, never()).resolve(anyString(), anyLong());
        verify(messageClient, never()).sendTimeoutReminder(anyLong(), anyString(), anyString(),
                anyString(), anyLong());
    }

    @Test
    @DisplayName("提醒开启：解析到用户后按租户投递，并在结束后清理租户上下文")
    void shouldSendReminderAndClearTenant() {
        properties.setNotifyEnabled(true);
        when(processTaskMapper.selectOverdue(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(row(1L, 9L, "manager")));
        when(userDirectoryClient.resolve("manager", 9L)).thenReturn(100L);
        when(messageClient.sendTimeoutReminder(eq(9L), anyString(), anyString(), anyString(), eq(100L)))
                .thenReturn(true);

        String result = scheduler.scan();

        assertTrue(result.contains("sent=1"));
        assertEquals(1.0, registry.find("workflow.task.overdue.reminder").tag("result", "SENT")
                .counter().count());
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("账号解析不到：跳过且不发消息")
    void shouldSkipWhenAccountUnresolved() {
        properties.setNotifyEnabled(true);
        when(processTaskMapper.selectOverdue(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(row(1L, 9L, "ghost")));
        when(userDirectoryClient.resolve("ghost", 9L)).thenReturn(null);

        String result = scheduler.scan();

        assertTrue(result.contains("skipped=1"));
        assertEquals(1.0, registry.find("workflow.task.overdue.reminder").tag("result", "SKIPPED")
                .counter().count());
        verify(messageClient, never()).sendTimeoutReminder(anyLong(), anyString(), anyString(),
                anyString(), anyLong());
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("投递失败：记 FAILED 指标但不抛异常")
    void shouldRecordFailureWhenSendFails() {
        properties.setNotifyEnabled(true);
        when(processTaskMapper.selectOverdue(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(row(1L, 9L, "manager")));
        when(userDirectoryClient.resolve("manager", 9L)).thenReturn(100L);
        when(messageClient.sendTimeoutReminder(anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(false);

        String result = scheduler.scan();

        assertTrue(result.contains("failed=1"));
        assertEquals(1.0, registry.find("workflow.task.overdue.reminder").tag("result", "FAILED")
                .counter().count());
    }

    /**
     * 构造一条超时待办。
     *
     * @param id 任务ID
     * @param tenantId 租户ID
     * @param assignee 审批人账号
     * @return 查询行
     */
    private static OverdueTaskRow row(Long id, Long tenantId, String assignee) {
        OverdueTaskRow row = new OverdueTaskRow();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setInstanceId(88L);
        row.setNodeName("部门审批");
        row.setAssignee(assignee);
        row.setDueAt(LocalDateTime.now().minusHours(1));
        return row;
    }
}
