package com.bluewhale.agent.support.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI 兼容客户端工具调用解析测试。
 *
 * @author BlueWhale
 */
class OpenAiClientToolTest {

    @Test
    void parsesToolCalls() {
        String body = """
                {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
                  {"id":"call_1","type":"function","function":{"name":"search_knowledge",
                   "arguments":"{\\"query\\":\\"端口\\",\\"topK\\":3}"}}]}}]}
                """;
        LlmToolTurn turn = OpenAiClient.parseToolTurn(body);
        assertTrue(turn.hasToolCalls());
        assertEquals(1, turn.toolCalls().size());
        assertEquals("call_1", turn.toolCalls().get(0).id());
        assertEquals("search_knowledge", turn.toolCalls().get(0).name());
        assertEquals("{\"query\":\"端口\",\"topK\":3}", turn.toolCalls().get(0).arguments());
    }

    @Test
    void parsesPlainTextResponse() {
        LlmToolTurn turn = OpenAiClient.parseToolTurn(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"结论 [S1]\"}}]}");
        assertFalse(turn.hasToolCalls());
        assertEquals("结论 [S1]", turn.content());
    }

    @Test
    void toleratesMalformedResponse() {
        assertNull(OpenAiClient.parseToolTurn("{not-json"));
    }
}
