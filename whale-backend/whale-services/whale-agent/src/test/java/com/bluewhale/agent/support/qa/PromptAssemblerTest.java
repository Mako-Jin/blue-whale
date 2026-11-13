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
                        "{\"port\":8090}", "PORT", "whale-agent", null, null),
                new Citation("S2", "chunk", "backend/whale-services/whale-gateway/src/main/resources/application.yml",
                        "gateway", 10, 20, "routes: []", null, null, 1.5D, null));
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

    @Test
    @DisplayName("直答模式：角色设定单独成条 system 消息，且不改变取证与历史顺序")
    void assembleWithPersona() {
        List<Citation> citations = List.of(
                new Citation("S1", "chunk", "docs/service-catalog.md", "目录", 1, 8,
                        "whale-agent 端口 8090", null, null, 1.0D, null));
        List<ChatMessage> history = List.of(ChatMessage.user("上一轮问题"), ChatMessage.assistant("上一轮回答"));

        List<ChatMessage> messages = assembler.assemble("户部主事是谁", citations, history,
                "  蓝鲸城户部主事沈文昭，管钱粮账目  ");

        assertEquals(5, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("system", messages.get(1).role());
        // 首尾空白要裁掉，避免模型把缩进当正文
        assertTrue(messages.get(1).content().contains("扮演：蓝鲸城户部主事沈文昭，管钱粮账目"));
        assertTrue(messages.get(1).content().startsWith("【角色设定】"));
        assertEquals("上一轮问题", messages.get(2).content());
        assertTrue(messages.get(4).content().contains("[S1]"));
    }

    /**
     * 角色设定为空串时退回原三参数形态，不多塞空 system。
     */
    @Test
    @DisplayName("角色设定为空白时不多塞 system 消息")
    void assembleWithoutPersona() {
        List<Citation> citations = List.of();
        List<ChatMessage> messages = assembler.assemble("问题", citations, List.of(), "   ");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("user", messages.get(1).role());
    }
}
