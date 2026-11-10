package com.bluewhale.workflow.support.machine;

import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StateMachine} 抄送节点（CC_TASK）非阻塞透传测试。
 *
 * @author BlueWhale
 */
class StateMachineCcTest {

    /** 含抄送节点的流程定义 */
    private static final String CC_MODEL_JSON = "{\"nodes\":["
            + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
            + "{\"key\":\"cc1\",\"type\":\"CC_TASK\",\"name\":\"抄送财务\",\"assignees\":[\"fin01\",\"fin02\"]},"
            + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"name\":\"部门审批\",\"assignee\":\"manager\"},"
            + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
            + "\"flows\":["
            + "{\"key\":\"f1\",\"from\":\"start\",\"to\":\"cc1\"},"
            + "{\"key\":\"f2\",\"from\":\"cc1\",\"to\":\"t1\"},"
            + "{\"key\":\"f3\",\"from\":\"t1\",\"to\":\"end\"}]}";

    @Test
    @DisplayName("start：抄送节点被收集并继续推进，停在后续用户任务")
    void shouldPassThroughCcNodeAndStopAtUserTask() {
        ProcessModel model = ProcessModelParser.parse(CC_MODEL_JSON);
        StateMachine machine = new StateMachine(model, Map.of());

        TransitionResult result = machine.start();

        assertTrue(result.isUserTask());
        assertEquals("t1", result.getState());
        assertEquals(1, result.getNodes().size());
        assertEquals("t1", result.getNodes().get(0).getKey());
        List<ProcessModelNode> ccNodes = result.getCcNodes();
        assertEquals(1, ccNodes.size());
        assertEquals("cc1", ccNodes.get(0).getKey());
        assertEquals(List.of("fin01", "fin02"), ccNodes.get(0).getAssignees());
    }

    @Test
    @DisplayName("parse：抄送节点缺少 assignees 抛异常")
    void shouldThrowWhenCcNodeHasNoRecipients() {
        String invalid = "{\"nodes\":[{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                + "{\"key\":\"cc1\",\"type\":\"CC_TASK\",\"name\":\"抄送\"},"
                + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"cc1\"},"
                + "{\"key\":\"f2\",\"from\":\"cc1\",\"to\":\"end\"}]}";

        assertThrows(RuntimeException.class, () -> ProcessModelParser.parse(invalid));
    }
}
