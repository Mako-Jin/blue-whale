package com.bluewhale.workflow.support.machine;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelException;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ValidationCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StateMachine} 选路、透传与终止语义单元测试。
 *
 * @author BlueWhale
 */
class StateMachineTest {

    /** legacy 两节点定义 */
    private static final String LEGACY_TWO_NODE =
            "{\"nodes\":[{\"key\":\"n1\",\"name\":\"一级审批\",\"assignee\":\"admin\"},"
                    + "{\"key\":\"n2\",\"name\":\"二级审批\",\"assignee\":\"user\"}]}";

    /** legacy 单节点定义 */
    private static final String LEGACY_ONE_NODE =
            "{\"nodes\":[{\"key\":\"n1\",\"name\":\"一级审批\",\"assignee\":\"admin\"}]}";

    /** 规范化定义：开始事件 → 网关（t1 / t2 默认）→ 结束事件 */
    private static final String NORMALIZED_GATEWAY =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"gw\",\"type\":\"EXCLUSIVE_GATEWAY\"},"
                    + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"admin\"},"
                    + "{\"key\":\"t2\",\"type\":\"USER_TASK\",\"assignee\":\"user\"},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"gw\"},"
                    + "{\"key\":\"f2\",\"from\":\"gw\",\"to\":\"t1\"},"
                    + "{\"key\":\"f3\",\"from\":\"gw\",\"to\":\"t2\",\"isDefault\":true},"
                    + "{\"key\":\"f4\",\"from\":\"t1\",\"to\":\"end\"},{\"key\":\"f5\",\"from\":\"t2\",\"to\":\"end\"}]}";

    /** 规范化定义：网关两分支均无默认 */
    private static final String NORMALIZED_NO_DEFAULT =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"gw\",\"type\":\"EXCLUSIVE_GATEWAY\"},"
                    + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"},"
                    + "{\"key\":\"t2\",\"type\":\"USER_TASK\",\"assignee\":\"b\"},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"gw\"},"
                    + "{\"key\":\"f2\",\"from\":\"gw\",\"to\":\"t1\"},{\"key\":\"f3\",\"from\":\"gw\",\"to\":\"t2\"},"
                    + "{\"key\":\"f4\",\"from\":\"t1\",\"to\":\"end\"},{\"key\":\"f5\",\"from\":\"t2\",\"to\":\"end\"}]}";

    /** 规范化定义：两个网关成环（透传跳数超限） */
    private static final String GATEWAY_LOOP =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"gw1\",\"type\":\"EXCLUSIVE_GATEWAY\"},"
                    + "{\"key\":\"gw2\",\"type\":\"EXCLUSIVE_GATEWAY\"},"
                    + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"}],"
                    + "\"flows\":[{\"key\":\"f1\",\"from\":\"gw1\",\"to\":\"gw2\"},"
                    + "{\"key\":\"f2\",\"from\":\"gw2\",\"to\":\"gw1\"}]}";

    @Test
    @DisplayName("start：legacy 停在首节点审批")
    void shouldStartLegacyAtFirstNode() {
        StateMachine machine = new StateMachine(ProcessModelParser.parse(LEGACY_TWO_NODE));

        TransitionResult result = machine.start();

        assertTrue(result.isUserTask());
        assertFalse(result.isTerminated());
        assertEquals("n1", result.getState());
        assertEquals("一级审批", result.getNode().getName());
        assertEquals("admin", result.getNode().getAssignee());
    }

    @Test
    @DisplayName("start：透传开始事件与网关，按默认分支抵达审批节点")
    void shouldStartThroughGatewayToDefaultBranch() {
        StateMachine machine = new StateMachine(ProcessModelParser.parse(NORMALIZED_GATEWAY));

        TransitionResult result = machine.start();

        assertTrue(result.isUserTask());
        assertEquals("t2", result.getState());
    }

    @Test
    @DisplayName("approve：非末节点推进到下一审批节点")
    void shouldApproveToNextUserTask() {
        StateMachine machine = new StateMachine(ProcessModelParser.parse(LEGACY_TWO_NODE));

        TransitionResult result = machine.approve("n1");

        assertTrue(result.isUserTask());
        assertEquals("n2", result.getState());
        assertEquals("user", result.getNode().getAssignee());
    }

    @Test
    @DisplayName("approve：末节点无出流终止")
    void shouldTerminateWhenNoOutgoing() {
        StateMachine machine = new StateMachine(ProcessModelParser.parse(LEGACY_ONE_NODE));

        TransitionResult result = machine.approve("n1");

        assertTrue(result.isTerminated());
        assertFalse(result.isUserTask());
        assertEquals("n1", result.getState());
    }

    @Test
    @DisplayName("approve：抵达结束事件终止")
    void shouldTerminateAtEndEvent() {
        StateMachine machine = new StateMachine(ProcessModelParser.parse(NORMALIZED_GATEWAY));

        TransitionResult result = machine.approve("t2");

        assertTrue(result.isTerminated());
        assertEquals("end", result.getState());
    }

    @Test
    @DisplayName("transitions：网关两分支且默认流挂 DefaultFlowGuard")
    void shouldListTransitionsWithDefaultGuard() {
        StateMachine machine = new StateMachine(ProcessModelParser.parse(NORMALIZED_GATEWAY));

        assertEquals(2, machine.transitions("gw").size());
        Transition picked = machine.pickTransition("gw");
        assertEquals("f3", picked.getFlow().getKey());
        assertEquals(DefaultFlowGuard.INSTANCE, picked.getGuard());
    }

    @Test
    @DisplayName("pickTransition：无出流返回 null")
    void shouldReturnNullWhenNoTransition() {
        StateMachine machine = new StateMachine(ProcessModelParser.parse(LEGACY_ONE_NODE));

        assertNull(machine.pickTransition("n1"));
    }

    @Test
    @DisplayName("pickTransition：多分支无默认抛 GATEWAY_MULTI_BRANCH_NO_DEFAULT")
    void shouldRejectMultiBranchWithoutDefault() {
        StateMachine machine = new StateMachine(ProcessModelParser.parse(NORMALIZED_NO_DEFAULT));

        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> machine.pickTransition("gw"));

        assertEquals(ValidationCode.GATEWAY_MULTI_BRANCH_NO_DEFAULT, ex.getValidationCode());
        assertEquals("gw", ex.getRefKey());
    }

    @Test
    @DisplayName("advance：网关透传跳数超限抛参数错误")
    void shouldRejectTooManyHops() {
        StateMachine machine = new StateMachine(ProcessModelParser.parse(GATEWAY_LOOP));

        BizException ex = assertThrows(BizException.class, () -> machine.advance("gw1"));

        assertEquals(400, ex.getCode());
        assertEquals("网关流转超出上限", ex.getMessage());
    }

    @Test
    @DisplayName("entryKey：无开始事件时回落首节点")
    void shouldFallbackEntryToFirstNode() {
        ProcessModel model = ProcessModelParser.parse(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"},"
                        + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                        + "\"flows\":[{\"key\":\"f1\",\"from\":\"t1\",\"to\":\"end\"}]}");

        assertEquals("t1", model.entryKey());
    }

    @Test
    @DisplayName("GuardContext：accepts 对 null 守卫返回 false")
    void shouldAcceptGuardContext() {
        ProcessModel model = ProcessModelParser.parse(NORMALIZED_GATEWAY);
        GuardContext context = GuardContext.of(model, model.node("gw"), StateMachineEvent.START);

        assertTrue(context.accepts(AlwaysGuard.INSTANCE, model.outgoing("gw").get(0)));
        assertFalse(context.accepts(null, model.outgoing("gw").get(0)));
        assertEquals(StateMachineEvent.START, context.getEvent());
    }
}
