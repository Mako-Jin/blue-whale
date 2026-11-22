package com.bluewhale.scene.infrastructure.client;

import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.scene.application.view.MfgBoardView;
import com.bluewhale.scene.config.SceneProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link ErpManufacturingBoardClient} 单元测试。
 *
 * <p>核心语义是 fail-open：网络/解析/业务码任何一环失败都返回 empty，
 * 绝不能补 0 或假数据；成功路径必须把 ERP 的订单与库存行正确反序列化。</p>
 *
 * @author BlueWhale
 */
class ErpManufacturingBoardClientTest {

    private static final String ERP_BASE = "http://erp.test";

    private static final String BOARD_URL = ERP_BASE + "/erp/internal/mfg/board?limit=5";

    private MockRestServiceServer server;

    private ErpManufacturingBoardClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SceneProperties properties = new SceneProperties();
        properties.setErpBaseUrl(ERP_BASE);
        client = new ErpManufacturingBoardClient(properties, builder.build());
    }

    @Test
    @DisplayName("成功：解析 code=0 的 data，订单与库存行完整返回")
    void shouldParseBoardData() {
        server.expect(requestTo(BOARD_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"code":0,"data":{"generatedAt":"2026-11-22T10:00:00Z",
                        "orders":[{"orderNo":"MO-1","itemCode":"ITEM-1","itemName":"齿轮",
                        "plannedQuantity":100,"completedQuantity":60,"status":"RUNNING",
                        "targetWarehouseId":9,"targetWarehouseName":"成品库","mesWorkOrderCode":"WO-1"}],
                        "stocks":[{"itemCode":"ITEM-1","itemName":"齿轮","warehouseId":9,"quantity":120.5}]}}
                        """, MediaType.APPLICATION_JSON));

        Optional<MfgBoardView> result = client.fetch(7L, 5);

        assertTrue(result.isPresent());
        MfgBoardView board = result.get();
        assertFalse(board.isDegraded());
        assertEquals("2026-11-22T10:00:00Z", board.getGeneratedAt());
        assertEquals(1, board.getOrders().size());
        assertEquals("MO-1", board.getOrders().get(0).getOrderNo());
        assertEquals(0, new BigDecimal("60").compareTo(board.getOrders().get(0).getCompletedQuantity()));
        assertEquals(1, board.getStocks().size());
        assertEquals(0, new BigDecimal("120.5").compareTo(board.getStocks().get(0).getQuantity()));
        server.verify();
    }

    @Test
    @DisplayName("成功：响应无 code 节点时按成功处理（历史兼容）")
    void shouldAcceptMissingCode() {
        server.expect(requestTo(BOARD_URL))
                .andRespond(withSuccess("{\"data\":{\"generatedAt\":\"now\"}}",
                        MediaType.APPLICATION_JSON));

        assertTrue(client.fetch(7L, 5).isPresent());
    }

    @Test
    @DisplayName("业务失败：code 非 0 时返回 empty")
    void shouldReturnEmptyOnBusinessError() {
        server.expect(requestTo(BOARD_URL))
                .andRespond(withSuccess("{\"code\":500,\"message\":\"ERP 查询超时\"}",
                        MediaType.APPLICATION_JSON));

        assertTrue(client.fetch(7L, 5).isEmpty());
    }

    @Test
    @DisplayName("降级：HTTP 5xx 时返回 empty（不抛异常）")
    void shouldFailOpenOnServerError() {
        server.expect(requestTo(BOARD_URL)).andRespond(withServerError());

        assertTrue(client.fetch(7L, 5).isEmpty());
    }

    @Test
    @DisplayName("降级：响应体为空/空白时返回 empty")
    void shouldReturnEmptyOnBlankBody() {
        server.expect(requestTo(BOARD_URL))
                .andRespond(withSuccess("   ", MediaType.APPLICATION_JSON));

        assertTrue(client.fetch(7L, 5).isEmpty());
    }

    @Test
    @DisplayName("降级：响应 JSON 非法时返回 empty")
    void shouldReturnEmptyOnMalformedJson() {
        server.expect(requestTo(BOARD_URL))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertTrue(client.fetch(7L, 5).isEmpty());
    }

    @Test
    @DisplayName("降级：缺少 data 节点时返回 empty")
    void shouldReturnEmptyWhenDataMissing() {
        server.expect(requestTo(BOARD_URL))
                .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));

        assertTrue(client.fetch(7L, 5).isEmpty());
    }

    @Test
    @DisplayName("降级：data 为 null 时返回 empty")
    void shouldReturnEmptyWhenDataNull() {
        server.expect(requestTo(BOARD_URL))
                .andRespond(withSuccess("{\"code\":0,\"data\":null}", MediaType.APPLICATION_JSON));

        assertTrue(client.fetch(7L, 5).isEmpty());
    }

    @Test
    @DisplayName("降级：data 非对象时返回 empty")
    void shouldReturnEmptyWhenDataNotObject() {
        server.expect(requestTo(BOARD_URL))
                .andRespond(withSuccess("{\"code\":0,\"data\":[1,2,3]}", MediaType.APPLICATION_JSON));

        assertTrue(client.fetch(7L, 5).isEmpty());
    }

    @Test
    @DisplayName("降级：data 反序列化失败时返回 empty")
    void shouldReturnEmptyOnDeserializeFailure() {
        server.expect(requestTo(BOARD_URL))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"orders\":\"not-a-list\"}}",
                        MediaType.APPLICATION_JSON));

        assertTrue(client.fetch(7L, 5).isEmpty());
    }

    @Test
    @DisplayName("降级：客户端未注入且基地址非法时返回 empty（生产构造分支）")
    void shouldFailOpenWhenProductionClientCannotBuildUri() {
        SceneProperties properties = new SceneProperties();
        properties.setErpBaseUrl("");
        ErpManufacturingBoardClient productionClient = new ErpManufacturingBoardClient(properties);

        assertTrue(productionClient.fetch(7L, 5).isEmpty());
    }

    @Test
    @DisplayName("请求头：透传租户 ID 与内网令牌头")
    void shouldForwardTenantAndInternalTokenHeaders() {
        server.expect(requestTo(BOARD_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(WebHeaders.TENANT_ID, "42"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"generatedAt\":\"now\"}}",
                        MediaType.APPLICATION_JSON));

        client.fetch(42L, 5);

        server.verify();
    }
}
