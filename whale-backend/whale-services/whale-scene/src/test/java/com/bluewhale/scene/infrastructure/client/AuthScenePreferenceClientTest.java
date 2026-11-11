package com.bluewhale.scene.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.scene.application.support.SceneViewModes;
import com.bluewhale.scene.application.view.ScenePreferenceView;
import com.bluewhale.scene.config.SceneProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link AuthScenePreferenceClient} 单元测试。
 *
 * <p>重点覆盖两类「静默失败」风险：读失败必须回落（fail-open）、
 * 写业务码非 0 必须抛错（fail-closed）。</p>
 *
 * @author BlueWhale
 */
class AuthScenePreferenceClientTest {

    /** auth 内网基地址（测试桩） */
    private static final String AUTH_BASE = "http://auth.test";

    private MockRestServiceServer server;

    private AuthScenePreferenceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SceneProperties properties = new SceneProperties();
        properties.setAuthBaseUrl(AUTH_BASE);
        client = new AuthScenePreferenceClient(properties, builder.build());
    }

    @Test
    @DisplayName("读到配置时解析出 viewMode 与 defaultSceneKey")
    void shouldParseStoredValue() {
        server.expect(requestTo(AUTH_BASE + "/user/internal/config/batch-get"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"scene.view\"")))
                .andRespond(withSuccess("""
                        {"code":0,"data":[{"userId":7,"configKey":"scene.view",
                        "value":"{\\"viewMode\\":\\"3D\\",\\"defaultSceneKey\\":\\"city-wall\\"}",
                        "source":"USER"}]}
                        """, MediaType.APPLICATION_JSON));

        Optional<ScenePreferenceView> result = client.read(1L, 7L);

        assertTrue(result.isPresent());
        assertEquals(SceneViewModes.VIEW_MODE_3D, result.get().getViewMode());
        assertEquals("city-wall", result.get().getDefaultSceneKey());
        server.verify();
    }

    @Test
    @DisplayName("未配置（value 为 null）时返回 empty")
    void shouldReturnEmptyWhenNotConfigured() {
        server.expect(requestTo(AUTH_BASE + "/user/internal/config/batch-get"))
                .andRespond(withSuccess("""
                        {"code":0,"data":[{"userId":7,"configKey":"scene.view","value":null,"source":"NONE"}]}
                        """, MediaType.APPLICATION_JSON));

        assertTrue(client.read(1L, 7L).isEmpty());
    }

    @Test
    @DisplayName("auth 不可用时返回 empty（fail-open，不抛错）")
    void shouldFailOpenWhenAuthUnavailable() {
        server.expect(requestTo(AUTH_BASE + "/user/internal/config/batch-get"))
                .andRespond(withServerError());

        assertTrue(client.read(1L, 7L).isEmpty());
    }

    @Test
    @DisplayName("配置值 JSON 非法时返回 empty（脏数据不阻塞进页面）")
    void shouldReturnEmptyOnBrokenValue() {
        server.expect(requestTo(AUTH_BASE + "/user/internal/config/batch-get"))
                .andRespond(withSuccess("""
                        {"code":0,"data":[{"userId":7,"configKey":"scene.view","value":"not-json","source":"USER"}]}
                        """, MediaType.APPLICATION_JSON));

        assertTrue(client.read(1L, 7L).isEmpty());
    }

    @Test
    @DisplayName("存储的 viewMode 被改坏时回落 2D")
    void shouldFallbackToTwoDOnInvalidStoredViewMode() {
        server.expect(requestTo(AUTH_BASE + "/user/internal/config/batch-get"))
                .andRespond(withSuccess("""
                        {"code":0,"data":[{"userId":7,"configKey":"scene.view",
                        "value":"{\\"viewMode\\":\\"4D\\"}","source":"USER"}]}
                        """, MediaType.APPLICATION_JSON));

        Optional<ScenePreferenceView> result = client.read(1L, 7L);

        assertTrue(result.isPresent());
        assertEquals(SceneViewModes.VIEW_MODE_2D, result.get().getViewMode());
        assertNull(result.get().getDefaultSceneKey());
    }

    @Test
    @DisplayName("写入时提交 scene.view 键与规范 JSON 值")
    void shouldWriteNormalizedValue() {
        server.expect(requestTo(AUTH_BASE + "/user/internal/config/set"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"configKey\":\"scene.view\"")))
                .andExpect(content().string(
                        containsString("{\\\"viewMode\\\":\\\"3D\\\",\\\"defaultSceneKey\\\":\\\"city-wall\\\"}")))
                .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));

        ScenePreferenceView view = new ScenePreferenceView();
        view.setViewMode(SceneViewModes.VIEW_MODE_3D);
        view.setDefaultSceneKey("city-wall");

        client.write(1L, 7L, view, 7L);

        server.verify();
    }

    @Test
    @DisplayName("写入业务码非 0 时抛错（fail-closed，不假装成功）")
    void shouldFailClosedOnBusinessError() {
        server.expect(requestTo(AUTH_BASE + "/user/internal/config/set"))
                .andRespond(withSuccess("{\"code\":400,\"message\":\"配置键不合法\"}",
                        MediaType.APPLICATION_JSON));

        ScenePreferenceView view = new ScenePreferenceView();
        view.setViewMode(SceneViewModes.VIEW_MODE_2D);

        BizException exception = assertThrows(BizException.class,
                () -> client.write(1L, 7L, view, 7L));

        assertTrue(exception.getMessage().contains("配置键不合法"));
    }
}
