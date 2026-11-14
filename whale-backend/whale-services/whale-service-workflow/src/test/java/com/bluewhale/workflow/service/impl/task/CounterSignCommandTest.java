package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.WorkflowConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CounterSignCommand} 会签判定单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class CounterSignCommandTest {

    @Mock
    private ProcessTaskMapper processTaskMapper;

    @InjectMocks
    private CounterSignCommand counterSignCommand;

    @Test
    @DisplayName("RATIO：已取消任务不参与分母，达成阈值时收敛同节点待办")
    void shouldIgnoreCanceledTasksInRatioDenominator() {
        ProcessTaskPo approved = task(WorkflowConstants.TASK_APPROVED);
        ProcessTaskPo pending = task(WorkflowConstants.TASK_PENDING);
        ProcessTaskPo canceled = task(WorkflowConstants.TASK_CANCELED);
        when(processTaskMapper.selectList(any())).thenReturn(List.of(approved, pending, canceled));

        boolean wait = counterSignCommand.awaitSameNode(5L, "t1", counterSignNode("RATIO:50"));

        assertFalse(wait);
        verify(processTaskMapper).update(any(ProcessTaskPo.class), any());
    }

    @Test
    @DisplayName("RATIO：未达阈值时继续等待且不取消待办")
    void shouldWaitWhenRatioNotSatisfied() {
        ProcessTaskPo approved = task(WorkflowConstants.TASK_APPROVED);
        ProcessTaskPo pending = task(WorkflowConstants.TASK_PENDING);
        when(processTaskMapper.selectList(any())).thenReturn(List.of(approved, pending));

        boolean wait = counterSignCommand.awaitSameNode(5L, "t1", counterSignNode("RATIO:80"));

        assertTrue(wait);
        verify(processTaskMapper, never()).update(any(ProcessTaskPo.class), any());
    }

    /**
     * 构造指定状态的任务。
     *
     * @param status 任务状态
     * @return 任务实体
     */
    private static ProcessTaskPo task(int status) {
        ProcessTaskPo task = new ProcessTaskPo();
        task.setInstanceId(5L);
        task.setNodeKey("t1");
        task.setStatus(status);
        return task;
    }

    /**
     * 构造会签节点。
     *
     * @param policy 会签策略
     * @return 会签节点
     */
    private static ProcessModelNode counterSignNode(String policy) {
        ProcessModelNode node = new ProcessModelNode();
        node.setKey("t1");
        node.setType(ProcessNodeType.COUNTER_SIGN);
        node.setCounterSignPolicy(policy);
        return node;
    }
}
