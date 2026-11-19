package com.bluewhale.message.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.message.config.MessageRecipientProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link AuthRecipientGroupClient} 接收人组客户端单元测试。
 *
 * @author BlueWhale
 */
class AuthRecipientGroupClientTest {

    private static final String BY_ROLE_URL =
            "http://127.0.0.1:8081/user/v1/internal/by-role?roleCode=tenant_admin";

    private final MessageRecipientProperties properties = new MessageRecipientProperties();

    private final RestClient.Builder builder = RestClient.builder();

    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private final AuthRecipientGroupClient client = new AuthRecipientGroupClient(
            properties, new ObjectMapper(), builder.build());

    @Test
    @DisplayName("解析 auth 响应中的用户ID")
    void shouldParseUserIds() {
        server.expect(requestTo(BY_ROLE_URL))
                .andRespond(withSuccess("{\"code\":0,\"data\":[{\"id\":11},{\"id\":12}]}",
                        MediaType.APPLICATION_JSON));

        List<Long> userIds = client.listMemberUserIds(1L, "tenant_admin");

        assertEquals(List.of(11L, 12L), userIds);
        server.verify();
    }

    @Test
    @DisplayName("业务码非 0 时抛 BizException，避免静默发空")
    void shouldThrowWhenBusinessCodeFails() {
        server.expect(requestTo(BY_ROLE_URL))
                .andRespond(withSuccess("{\"code\":500,\"message\":\"db down\"}",
                        MediaType.APPLICATION_JSON));

        assertThrows(BizException.class, () -> client.listMemberUserIds(1L, "tenant_admin"));
    }

    @Test
    @DisplayName("auth 不可用时抛 BizException")
    void shouldThrowWhenHttpError() {
        server.expect(requestTo(BY_ROLE_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(BizException.class, () -> client.listMemberUserIds(1L, "tenant_admin"));
    }

    @Test
    @DisplayName("缺少角色编码时不发起调用")
    void shouldReturnEmptyWithoutCallingAuth() {
        assertEquals(List.of(), client.listMemberUserIds(1L, " "));
        server.verify();
    }
}
