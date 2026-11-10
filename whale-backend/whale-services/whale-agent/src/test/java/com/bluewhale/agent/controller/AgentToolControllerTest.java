package com.bluewhale.agent.controller;

import com.bluewhale.agent.dto.DocReadToolRequest;
import com.bluewhale.agent.dto.FactGetToolRequest;
import com.bluewhale.agent.dto.KnowledgeSearchToolRequest;
import com.bluewhale.agent.support.mcp.McpToolService;
import com.bluewhale.common.core.result.Result;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 智能体只读工具接口测试：入参映射与内核委托。
 *
 * @author BlueWhale
 */
class AgentToolControllerTest {

    private final McpToolService toolService = mock(McpToolService.class);

    private final AgentToolController controller = new AgentToolController(toolService);

    @Test
    @SuppressWarnings("unchecked")
    void knowledgeSearchMapsArguments() {
        Map<String, Object> payload = Map.of("count", 1);
        when(toolService.searchKnowledge(any())).thenReturn(payload);
        KnowledgeSearchToolRequest request = new KnowledgeSearchToolRequest();
        request.setQuery("端口分配");
        request.setTopK(5);
        request.setKinds(List.of("doc", "config"));

        Result<Map<String, Object>> result = controller.knowledgeSearch(request);

        assertSame(payload, result.getData());
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(toolService).searchKnowledge(captor.capture());
        assertEquals("端口分配", captor.getValue().get("query"));
        assertEquals(5, captor.getValue().get("topK"));
        assertEquals(List.of("doc", "config"), captor.getValue().get("kinds"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void factGetMapsArguments() {
        when(toolService.getFact(any())).thenReturn(Map.of("count", 0));
        FactGetToolRequest request = new FactGetToolRequest();
        request.setType("PORT");
        request.setKey("8092");
        request.setLimit(10);

        controller.factGet(request);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(toolService).getFact(captor.capture());
        assertEquals("PORT", captor.getValue().get("type"));
        assertEquals("8092", captor.getValue().get("key"));
        assertEquals(10, captor.getValue().get("limit"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void docReadMapsArguments() {
        when(toolService.readFile(any())).thenReturn(Map.of("content", "x"));
        DocReadToolRequest request = new DocReadToolRequest();
        request.setPath("docs/service-catalog.md");
        request.setStartLine(3);
        request.setEndLine(9);

        controller.docRead(request);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(toolService).readFile(captor.capture());
        assertEquals("docs/service-catalog.md", captor.getValue().get("path"));
        assertEquals(3, captor.getValue().get("startLine"));
        assertEquals(9, captor.getValue().get("endLine"));
    }
}
