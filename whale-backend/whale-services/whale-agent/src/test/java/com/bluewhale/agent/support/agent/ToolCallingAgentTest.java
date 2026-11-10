package com.bluewhale.agent.support.agent;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.support.llm.ChatMessage;
import com.bluewhale.agent.support.llm.LlmClient;
import com.bluewhale.agent.support.llm.LlmToolCall;
import com.bluewhale.agent.support.llm.LlmToolSpec;
import com.bluewhale.agent.support.llm.LlmToolTurn;
import com.bluewhale.agent.support.mcp.McpToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 自主编排循环测试：多步取证、未知工具容错、去重、步数上限与模型失败降级。
 *
 * @author BlueWhale
 */
class ToolCallingAgentTest {

    private McpToolService toolService;

    private KnowledgeToolRegistry registry;

    private AgentProperties properties;

    @BeforeEach
    void setUp() {
        toolService = mock(McpToolService.class);
        registry = new KnowledgeToolRegistry(toolService);
        properties = new AgentProperties();
    }

    @Test
    void collectsEvidenceThenUsesFinalAnswer() {
        when(toolService.searchKnowledge(any())).thenReturn(searchResult("docs/service-catalog.md"));
        ScriptedLlm llm = new ScriptedLlm();
        llm.enqueue(call("search_knowledge", "{\"query\":\"端口\"}"));
        llm.enqueue(LlmToolTurn.text("whale-agent 端口为 8090 [S1]"));

        List<ToolTrace> observed = new ArrayList<>();
        ToolCallingResult result = new ToolCallingAgent(llm, registry, properties)
                .run(List.of(), "whale-agent 端口是多少？", "code,doc",
                        (trace, evidence) -> observed.add(trace));

        assertEquals("whale-agent 端口为 8090 [S1]", result.content());
        assertEquals(1, result.evidence().size());
        assertEquals(1, result.traces().size());
        assertEquals(1, observed.size());
        assertTrue(result.traces().get(0).success());
        assertEquals("search_knowledge", result.traces().get(0).name());
        assertEquals(1, result.traces().get(0).evidenceCount());
        assertFalse(result.degraded());
        assertTrue(result.transcript().contains("S1"));
    }

    @Test
    void unknownToolIsReportedAndLoopContinues() {
        ScriptedLlm llm = new ScriptedLlm();
        llm.enqueue(call("drop_database", "{}"));
        llm.enqueue(LlmToolTurn.text("无法执行写操作，给出只读结论"));

        ToolCallingResult result = new ToolCallingAgent(llm, registry, properties)
                .run(List.of(), "删除数据", null, null);

        assertEquals("无法执行写操作，给出只读结论", result.content());
        assertEquals(1, result.traces().size());
        assertFalse(result.traces().get(0).success());
        assertTrue(result.traces().get(0).error().contains("未知工具"));
        assertTrue(result.evidence().isEmpty());
        assertFalse(result.degraded());
    }

    @Test
    void duplicateEvidenceIsRegisteredOnce() {
        when(toolService.searchKnowledge(any())).thenReturn(searchResult("docs/plans/roadmap.md"));
        ScriptedLlm llm = new ScriptedLlm();
        llm.enqueue(new LlmToolTurn(null, List.of(
                new LlmToolCall("c1", "search_knowledge", "{\"query\":\"路线图\"}"),
                new LlmToolCall("c2", "search_knowledge", "{\"query\":\"路线图\"}"))));
        llm.enqueue(LlmToolTurn.text("结论 [S1]"));

        ToolCallingResult result = new ToolCallingAgent(llm, registry, properties)
                .run(List.of(), "路线图", null, null);

        assertEquals(1, result.evidence().size());
        assertEquals(2, result.traces().size());
        assertEquals(1, result.traces().get(0).evidenceCount());
        assertEquals(0, result.traces().get(1).evidenceCount());
    }

    @Test
    void stopsAtMaxSteps() {
        properties.getQa().setMaxToolSteps(2);
        when(toolService.searchKnowledge(any())).thenReturn(searchResult("docs/README.md"));
        ScriptedLlm llm = new ScriptedLlm();
        llm.enqueue(call("search_knowledge", "{\"query\":\"a\"}"));
        llm.enqueue(call("search_knowledge", "{\"query\":\"b\"}"));
        llm.enqueue(call("search_knowledge", "{\"query\":\"c\"}"));

        ToolCallingResult result = new ToolCallingAgent(llm, registry, properties)
                .run(List.of(), "循环问题", null, null);

        assertEquals(2, result.traces().size());
        assertTrue(result.content().contains("最大步数"));
        assertFalse(result.degraded());
    }

    @Test
    void fallsBackWhenModelReturnsNull() {
        ToolCallingResult result = new ToolCallingAgent(new ScriptedLlm(), registry, properties)
                .run(List.of(), "任意问题", null, null);

        assertTrue(result.degraded());
        assertTrue(result.content().contains("模型调用失败"));
        assertTrue(result.content().contains("已检索到的资料"));
    }

    @Test
    void executionFailureDoesNotAbortLoop() {
        when(toolService.searchKnowledge(any())).thenThrow(new IllegalArgumentException("路径必须位于仓库内"));
        ScriptedLlm llm = new ScriptedLlm();
        llm.enqueue(call("search_knowledge", "{\"query\":\"x\"}"));
        llm.enqueue(LlmToolTurn.text("工具失败后仍作答"));

        ToolCallingResult result = new ToolCallingAgent(llm, registry, properties)
                .run(List.of(), "问题", null, null);

        assertEquals("工具失败后仍作答", result.content());
        assertFalse(result.traces().get(0).success());
        assertTrue(result.traces().get(0).error().contains("仓库内"));
    }

    /**
     * 构造工具调用响应。
     *
     * @param name 工具名
     * @param args 参数
     * @return 响应
     */
    private static LlmToolTurn call(String name, String args) {
        return new LlmToolTurn(null, List.of(new LlmToolCall("call-" + name, name, args)));
    }

    /**
     * 构造检索工具返回值。
     *
     * @param path 路径
     * @return 工具返回值
     */
    private static Map<String, Object> searchResult(String path) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("path", path);
        item.put("startLine", 1);
        item.put("endLine", 10);
        item.put("title", "示例");
        item.put("snippet", "片段");
        item.put("score", 1.5D);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", "q");
        result.put("count", 1);
        result.put("items", List.of(item));
        return result;
    }

    /**
     * 脚本化模型客户端：按队列依次返回响应，耗尽后返回 {@code null}。
     */
    private static final class ScriptedLlm implements LlmClient {

        private final Deque<LlmToolTurn> turns = new ArrayDeque<>();

        /**
         * 追加一个响应。
         *
         * @param turn 响应
         */
        void enqueue(LlmToolTurn turn) {
            turns.add(turn);
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String complete(List<ChatMessage> messages) {
            LlmToolTurn turn = turns.poll();
            return turn == null ? null : turn.content();
        }

        @Override
        public LlmToolTurn completeWithTools(List<ChatMessage> messages, List<LlmToolSpec> tools) {
            assertNotNull(messages);
            assertFalse(messages.isEmpty());
            return turns.poll();
        }

        @Override
        public void stream(List<ChatMessage> messages, Consumer<String> onDelta) {
            LlmToolTurn turn = turns.poll();
            if (turn != null && turn.content() != null) {
                onDelta.accept(turn.content());
            }
        }
    }
}
