package com.bluewhale.message.channel.impl;

import com.bluewhale.common.internaltoken.InternalTokenProperties;
import com.bluewhale.message.channel.ChannelMessage;
import com.bluewhale.message.channel.ChannelResult;
import com.bluewhale.message.config.MessageChannelProperties;
import com.bluewhale.message.dto.MessageRecipient;
import com.bluewhale.message.enums.DeliveryStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IM 渠道适配器集成测试：用本地 HTTP 服务模拟 whale-im 内网入口，校验投递协议与回执标识绑定。
 *
 * <p>覆盖链路起点：消息服务 → whale-im 的 {@code /im/internal/notify} 请求体
 * （{@code clientMsgId}、{@code tenantId}）与 {@code providerMessageId} 回写。</p>
 *
 * @author BlueWhale
 */
class ImChannelSenderIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<CapturedRequest> captured = new CopyOnWriteArrayList<>();

    private HttpServer server;

    private ImChannelSender sender;

    /**
     * 启动本地模拟 IM 服务并构造渠道适配器。
     *
     * @throws IOException 端口绑定失败
     */
    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/im/internal/notify", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            captured.add(new CapturedRequest(exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(), body));
            byte[] response = "1".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        MessageChannelProperties properties = new MessageChannelProperties();
        properties.setMock(false);
        properties.getIm().setEnabled(true);
        properties.getIm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        sender = new ImChannelSender(properties, MAPPER, new InternalTokenProperties());
        sender.initHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("投递携带 clientMsgId 与 tenantId，并以 clientMsgId 作为 providerMessageId")
    void shouldBindClientMsgIdAsProviderMessageId() throws Exception {
        ChannelResult result = sender.send(message(9001L, 7L, 456L));

        assertEquals(DeliveryStatus.SENT, result.getStatus());
        assertEquals("msg-9001-456", result.getProviderMessageId());
        assertEquals(1, captured.size());
        CapturedRequest request = captured.get(0);
        assertEquals("POST", request.method());
        assertEquals("/im/internal/notify", request.path());
        JsonNode payload = MAPPER.readTree(request.body());
        assertEquals("msg-9001-456", payload.path("clientMsgId").asText());
        assertEquals(7L, payload.path("tenantId").asLong());
        assertEquals(456L, payload.path("userIds").get(0).asLong());
        assertEquals("ASSIGN", payload.path("bizType").asText());
        assertEquals("101", payload.path("bizId").asText());
    }

    @Test
    @DisplayName("接收人缺少 userId 时不发起请求，直接跳过")
    void shouldSkipWhenUserIdMissing() {
        ChannelMessage message = message(9002L, 7L, null);

        ChannelResult result = sender.send(message);

        assertEquals(DeliveryStatus.SKIPPED, result.getStatus());
        assertTrue(captured.isEmpty());
    }

    /**
     * 构造测试消息。
     *
     * @param messageId 消息ID
     * @param tenantId  租户ID
     * @param userId    接收人ID
     * @return 渠道消息
     */
    private static ChannelMessage message(Long messageId, Long tenantId, Long userId) {
        MessageRecipient recipient = new MessageRecipient();
        recipient.setUserId(userId);
        return ChannelMessage.builder()
                .messageId(messageId)
                .tenantId(tenantId)
                .bizType("ASSIGN")
                .bizId("101")
                .title("你有一条新的指派")
                .content("工单 #101 已指派给你")
                .recipient(recipient)
                .build();
    }

    /**
     * 捕获到的请求。
     *
     * @param method 方法
     * @param path   路径
     * @param body   请求体
     */
    private record CapturedRequest(String method, String path, String body) {
    }
}
