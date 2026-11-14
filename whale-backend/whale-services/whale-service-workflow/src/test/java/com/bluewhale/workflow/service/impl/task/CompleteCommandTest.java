package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.request.ApprovalRequest;
import com.bluewhale.workflow.request.TaskCompleteRequest;
import com.bluewhale.workflow.support.WorkflowConstants;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CompleteCommand} 内网完成回调幂等单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class CompleteCommandTest {

    @Mock
    private ProcessTaskMapper processTaskMapper;

    @Mock
    private ApproveCommand approveCommand;

    @Mock
    private RejectCommand rejectCommand;

    private CompleteCommand completeCommand;

    /**
     * 初始化实体表元数据，供 LambdaUpdateWrapper 解析列名
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProcessTaskPo.class);
    }

    @BeforeEach
    void setUp() {
        completeCommand = new CompleteCommand(processTaskMapper, approveCommand, rejectCommand);
    }

    /**
     * 构造待办任务桩对象。
     *
     * @param lastEventId 最近完成事件ID
     * @return 任务实体
     */
    private static ProcessTaskPo pendingTask(String lastEventId) {
        ProcessTaskPo task = new ProcessTaskPo();
        task.setId(100L);
        task.setInstanceId(5L);
        task.setStatus(WorkflowConstants.TASK_PENDING);
        task.setLastEventId(lastEventId);
        return task;
    }

    /**
     * 构造指定事件ID的完成请求。
     *
     * @param eventId 事件ID
     * @return 完成请求
     */
    private static TaskCompleteRequest request(String eventId) {
        TaskCompleteRequest request = new TaskCompleteRequest();
        request.setAction("APPROVE");
        request.setEventId(eventId);
        return request;
    }

    @Test
    @DisplayName("complete：同一 eventId 重复回调幂等返回，不再推进流程")
    void shouldReturnIdempotentWhenSameEventId() {
        when(processTaskMapper.selectById(100L)).thenReturn(pendingTask("e1"), pendingTask("e1"));
        when(processTaskMapper.update(ArgumentMatchers.<ProcessTaskPo>isNull(),
                ArgumentMatchers.<Wrapper<ProcessTaskPo>>any())).thenReturn(0);

        completeCommand.complete(100L, request("e1"));

        verifyNoInteractions(approveCommand, rejectCommand);
    }

    @Test
    @DisplayName("complete：eventId 已被其它请求占用时拒绝")
    void shouldRejectWhenEventIdUsedByAnotherRequest() {
        when(processTaskMapper.selectById(100L)).thenReturn(pendingTask(null), pendingTask("other"));
        when(processTaskMapper.update(ArgumentMatchers.<ProcessTaskPo>isNull(),
                ArgumentMatchers.<Wrapper<ProcessTaskPo>>any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> completeCommand.complete(100L, request("e1")));

        assertEquals(400, ex.getCode());
        assertEquals("任务已处理", ex.getMessage());
        verifyNoInteractions(approveCommand, rejectCommand);
    }

    @Test
    @DisplayName("complete：事件占位成功后继续推进流程")
    void shouldApproveAfterEventClaimed() {
        when(processTaskMapper.selectById(100L)).thenReturn(pendingTask(null));
        when(processTaskMapper.update(ArgumentMatchers.<ProcessTaskPo>isNull(),
                ArgumentMatchers.<Wrapper<ProcessTaskPo>>any())).thenReturn(1);
        when(processTaskMapper.update(any(ProcessTaskPo.class),
                ArgumentMatchers.<Wrapper<ProcessTaskPo>>any())).thenReturn(1);

        completeCommand.complete(100L, request("e2"));

        verify(approveCommand).approve(eq(100L), any(ApprovalRequest.class));
        verify(rejectCommand, never()).reject(any(), any());
    }

    @Test
    @DisplayName("complete：操作来源更新冲突时拒绝")
    void shouldRejectWhenOperatorUpdateConflicts() {
        when(processTaskMapper.selectById(100L)).thenReturn(pendingTask(null));
        when(processTaskMapper.update(ArgumentMatchers.<ProcessTaskPo>isNull(),
                ArgumentMatchers.<Wrapper<ProcessTaskPo>>any())).thenReturn(1);
        when(processTaskMapper.update(any(ProcessTaskPo.class),
                ArgumentMatchers.<Wrapper<ProcessTaskPo>>any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> completeCommand.complete(100L, request(null)));

        assertEquals("任务已处理", ex.getMessage());
        verifyNoInteractions(approveCommand, rejectCommand);
    }
}
