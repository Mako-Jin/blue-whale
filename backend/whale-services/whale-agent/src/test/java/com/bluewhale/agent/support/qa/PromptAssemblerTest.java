package com.bluewhale.agent.support.qa;

import com.bluewhale.agent.support.llm.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提示词组装测试：引用编号、证据包裹与历史顺序。
 *
 * @author BlueWhale
 */
class PromptAssemblerTest {

    private final PromptAssembler assembler = new PromptAssembler();

    @Test
    @DisplayName("事实与证据块按编号注入，source 标签隔离资料")
    void assemble() {
        List<Citation> citations = List.of(
                new Citation("S1", "fact", "docs/service-catalog.md", "whale-agent", null, null,
                        "{\"port\":8090}", "PORT", "whale-agent", null),
                new Citation("S2", "chunk", "backend/whale-services/whale-gateway/src/main/resources/application.yml",
                        "gateway", 10, 20, "routes: []", null, null, 1.5D));
        List<ChatMessage> history = List.of(ChatMessage.user("上一轮问题"), ChatMessage.assistant("上一轮回答"));

        List<ChatMessage> messages = assembler.assemble("whale-agent 端口是多少", citations, history);

        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("上一轮问题", messages.get(1).content());
        assertEquals("上一轮回答", messages.get(2).content());
        String user = messages.get(3).content();
        assertTrue(user.contains("[S1] PORT whale-agent"));
        assertTrue(user.contains("[S2]"));
        assertTrue(user.contains("<source id=\"S2\""));
        assertTrue(user.contains("lines=\"10-20\""));
    }
}
