package com.bluewhale.agent.support.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MCP JSON-RPC 分发测试。
 *
 * @author BlueWhale
 */
class McpRequestHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpToolService toolService;

    private McpRequestHandler handler;

    @BeforeEach
    void setUp() {
        toolService = mock(McpToolService.class);
        handler = new McpRequestHandler(toolService);
    }

    @Test
    void initializeReturnsProtocolVersionAndServerInfo() throws Exception {
        JsonNode response = MAPPER.readTree(handler.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"));

        assertEquals(McpRequestHandler.PROTOCOL_VERSION,
                response.path("result").path("protocolVersion").asText());
        assertEquals("bluewhale-agent-mcp", response.path("result").path("serverInfo").path("name").asText());
        assertEquals(1, response.path("id").asInt());
    }

    @Test
    void toolsListExposesFourReadOnlyTools() throws Exception {
        JsonNode response = MAPPER.readTree(handler.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"));

        JsonNode tools = response.path("result").path("tools");
        assertEquals(4, tools.size());
        List<String> names = List.of(tools.get(0).path("name").asText(), tools.get(1).path("name").asText(),
                tools.get(2).path("name").asText(), tools.get(3).path("name").asText());
        assertTrue(names.containsAll(List.of("search_knowledge", "get_fact", "read_file", "list_services")));
    }

    @Test
    void notificationsInitializedReturnsNoResponse() {
        assertNull(handler.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
    }

    @Test
    void unknownMethodReturnsMethodNotFound() throws Exception {
        JsonNode response = MAPPER.readTree(handler.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"resources/list\"}"));

        assertEquals(-32601, response.path("error").path("code").asInt());
    }

    @Test
    void malformedJsonReturnsParseError() throws Exception {
        JsonNode response = MAPPER.readTree(handler.handle("{not-json"));

        assertEquals(-32700, response.path("error").path("code").asInt());
    }

    @Test
    void toolsCallWrapsToolFailureAsErrorContent() throws Exception {
        when(toolService.readFile(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("路径必须位于仓库内"));

        JsonNode response = MAPPER.readTree(handler.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":"
                        + "{\"name\":\"read_file\",\"arguments\":{\"path\":\"../../etc/passwd\"}}}"));

        assertTrue(response.path("result").path("isError").asBoolean());
        assertTrue(response.path("result").path("content").get(0).path("text").asText()
                .contains("路径必须位于仓库内"));
    }

    @Test
    void toolsCallSerializesSuccessfulPayload() throws Exception {
        when(toolService.listServices()).thenReturn(Map.of("services", List.of(), "ports", List.of()));

        JsonNode response = MAPPER.readTree(handler.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":"
                        + "{\"name\":\"list_services\",\"arguments\":{}}}"));

        assertEquals(false, response.path("result").path("isError").asBoolean());
        assertTrue(response.path("result").path("content").get(0).path("text").asText().contains("services"));
    }
}
