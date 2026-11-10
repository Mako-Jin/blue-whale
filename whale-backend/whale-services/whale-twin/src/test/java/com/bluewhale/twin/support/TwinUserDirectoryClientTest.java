package com.bluewhale.twin.support;

import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.TwinNotifyRecipientVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 接收人检索客户端单测：请求路径、字段映射与失败折算为空列表。
 *
 * @author BlueWhale
 */
class TwinUserDirectoryClientTest {

    private static final String AUTH_URL = "http://127.0.0.1:8081";

    private static final Long TENANT_ID = 1L;

    private static final String DIRECTORY_URL = AUTH_URL
            + "/user/internal/directory?keyword=zhang&current=1&size=20";

    private MockRestServiceServer server;

    private TwinUserDirectoryClient client;

    @BeforeEach
    void setUp() {
        TwinProperties properties = new TwinProperties();
        properties.getAlertNotify().setAuthBaseUrl(AUTH_URL);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TwinUserDirectoryClient(properties, builder.build());
    }

    @Test
    void mapsDirectoryRowsToRecipients() {
        server.expect(requestTo(DIRECTORY_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Tenant-Id", "1"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"records\":["
                        + "{\"id\":1900000000000000001,\"username\":\"zhangsan\","
                        + "\"nickname\":\"张三\",\"deptName\":\"设备部\"},"
                        + "{\"id\":2,\"username\":\"zhangsi\"}]}}", MediaType.APPLICATION_JSON));

        List<TwinNotifyRecipientVO> recipients = client.search(TENANT_ID, "zhang", null);

        assertEquals(2, recipients.size());
        assertEquals("1900000000000000001", recipients.get(0).getId());
        assertEquals("张三", recipients.get(0).getName());
        assertEquals("设备部", recipients.get(0).getDeptName());
        // 没昵称时回落账号名
        assertEquals("zhangsi", recipients.get(1).getName());
        assertNull(recipients.get(1).getDeptName());
        server.verify();
    }

    @Test
    void returnsEmptyListOnBusinessError() {
        server.expect(requestTo(DIRECTORY_URL))
                .andRespond(withSuccess("{\"code\":500,\"message\":\"服务异常\"}",
                        MediaType.APPLICATION_JSON));

        assertTrue(client.search(TENANT_ID, "zhang", null).isEmpty());
    }

    @Test
    void returnsEmptyListOnHttpError() {
        server.expect(requestTo(DIRECTORY_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // 选择器拉不到人时前端可退回手工输入，不该整页报错
        assertTrue(client.search(TENANT_ID, "zhang", null).isEmpty());
    }

    @Test
    void skipsRowsWithoutId() {
        server.expect(requestTo(DIRECTORY_URL))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"records\":["
                        + "{\"username\":\"nobody\"},{\"id\":\"3\",\"username\":\"ok\"}]}}",
                        MediaType.APPLICATION_JSON));

        List<TwinNotifyRecipientVO> recipients = client.search(TENANT_ID, "zhang", null);

        assertEquals(1, recipients.size());
        assertEquals("3", recipients.get(0).getId());
    }
}
