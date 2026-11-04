package com.bluewhale.workflow.support;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AssigneeOverrides} 解析 / 序列化 / 校验测试。
 *
 * @author BlueWhale
 */
class AssigneeOverridesTest {

    private static final int PARAM_ERROR_CODE = 400;

    /** 含用户任务、抄送节点、会签节点的流程定义 */
    private static final String MODEL_JSON = "{\"nodes\":["
            + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
            + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"name\":\"部门审批\",\"assignee\":\"manager\"},"
            + "{\"key\":\"cs1\",\"type\":\"COUNTER_SIGN\",\"name\":\"会签\",\"assignees\":[\"a\",\"b\"]},"
            + "{\"key\":\"cc1\",\"type\":\"CC_TASK\",\"name\":\"抄送\",\"assignees\":[\"fin01\"]},"
            + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
            + "\"flows\":["
            + "{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
            + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"cs1\"},"
            + "{\"key\":\"f3\",\"from\":\"cs1\",\"to\":\"cc1\"},"
            + "{\"key\":\"f4\",\"from\":\"cc1\",\"to\":\"end\"}]}";

    @Test
    @DisplayName("parse：空值返回空 Map")
    void shouldReturnEmptyWhenParseBlank() {
        assertEquals(Map.of(), AssigneeOverrides.parse(null));
        assertEquals(Map.of(), AssigneeOverrides.parse("  "));
        assertEquals(Map.of(), AssigneeOverrides.parse("not-json"));
    }

    @Test
    @DisplayName("write / parse：可往返")
    void shouldRoundTrip() {
        Map<String, List<String>> overrides = Map.of("t1", List.of("lisi", "lisi2"));
        String json = AssigneeOverrides.write(overrides);
        assertEquals(overrides, AssigneeOverrides.parse(json));
        assertNull(AssigneeOverrides.write(Map.of()));
    }

    @Test
    @DisplayName("normalize：去空白并去重")
    void shouldTrimAndDeduplicate() {
        ProcessModel model = ProcessModelParser.parse(MODEL_JSON);

        Map<String, List<String>> normalized = AssigneeOverrides.normalize(model,
                Map.of("t1", List.of(" lisi ", "lisi", "wangwu")));

        assertEquals(List.of("lisi", "wangwu"), normalized.get("t1"));
    }

    @Test
    @DisplayName("normalize：未知节点抛 400")
    void shouldThrowWhenNodeUnknown() {
        ProcessModel model = ProcessModelParser.parse(MODEL_JSON);

        BizException ex = assertThrows(BizException.class,
                () -> AssigneeOverrides.normalize(model, Map.of("nope", List.of("a"))));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("normalize：空账号列表抛 400")
    void shouldThrowWhenAccountsEmpty() {
        ProcessModel model = ProcessModelParser.parse(MODEL_JSON);

        BizException ex = assertThrows(BizException.class,
                () -> AssigneeOverrides.normalize(model, Map.of("t1", List.of())));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("normalize：会签节点不允许覆盖")
    void shouldThrowWhenCounterSignOverridden() {
        ProcessModel model = ProcessModelParser.parse(MODEL_JSON);

        BizException ex = assertThrows(BizException.class,
                () -> AssigneeOverrides.normalize(model, Map.of("cs1", List.of("a"))));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("normalize：抄送节点允许覆盖")
    void shouldAllowCcOverride() {
        ProcessModel model = ProcessModelParser.parse(MODEL_JSON);

        Map<String, List<String>> normalized = AssigneeOverrides.normalize(model,
                Map.of("cc1", List.of("fin02")));

        assertEquals(List.of("fin02"), normalized.get("cc1"));
    }
}
