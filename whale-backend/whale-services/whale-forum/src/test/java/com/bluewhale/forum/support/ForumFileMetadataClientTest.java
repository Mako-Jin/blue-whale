package com.bluewhale.forum.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.config.ForumProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 申诉材料文件客户端单测：路径与租户头、元数据映射、归属与可用性 fail-closed。
 *
 * @author BlueWhale
 */
class ForumFileMetadataClientTest {

    private static final String FILE_URL = "http://127.0.0.1:8085/file/internal/801";

    private MockRestServiceServer server;

    private ForumFileMetadataClient client;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        ForumProperties properties = new ForumProperties();
        properties.setFileBaseUrl("http://127.0.0.1:8085");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ForumFileMetadataClient(properties, builder.build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("读取元数据：路径带租户头，字段按文件服务返回映射")
    void mapsMetadata() {
        server.expect(requestTo(FILE_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Tenant-Id", "1"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"originalName\":\"证据.png\","
                        + "\"fileSize\":2048,\"contentType\":\"image/png\",\"uploaderId\":100,"
                        + "\"deleted\":0,\"status\":1}}", MediaType.APPLICATION_JSON));

        Optional<ForumFileMetadataClient.FileMeta> meta = client.find(801L);

        assertThat(meta).isPresent();
        assertThat(meta.get().originalName()).isEqualTo("证据.png");
        assertThat(meta.get().fileSize()).isEqualTo(2048L);
        assertThat(meta.get().contentType()).isEqualTo("image/png");
        assertThat(meta.get().uploaderId()).isEqualTo(100L);
        server.verify();
    }

    @Test
    @DisplayName("文件不存在：404 折算为空，不抛异常")
    void missingFileReturnsEmpty() {
        server.expect(requestTo(FILE_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.find(801L)).isEmpty();
    }

    @Test
    @DisplayName("归属校验：他人文件拒绝引用")
    void rejectsForeignFile() {
        server.expect(requestTo(FILE_URL))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"originalName\":\"a.png\","
                        + "\"uploaderId\":200,\"deleted\":0,\"status\":1}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.requireOwned(801L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("本人");
    }

    @Test
    @DisplayName("归属校验：已删除或停用文件拒绝引用")
    void rejectsDeletedFile() {
        server.expect(requestTo(FILE_URL))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"uploaderId\":100,"
                        + "\"deleted\":1,\"status\":1}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.requireOwned(801L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在或已删除");
    }

    @Test
    @DisplayName("文件服务异常：fail-closed，不允许绕过校验提交")
    void failsClosedWhenFileServiceUnavailable() {
        server.expect(requestTo(FILE_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.find(801L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("暂不可用");
    }

    @Test
    @DisplayName("代理预览：读取内网内容并保留 MIME 类型")
    void loadsContent() {
        server.expect(requestTo("http://127.0.0.1:8085/file/internal/801/content"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Tenant-Id", "1"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_PNG));

        ForumFileMetadataClient.FileContent content = client.loadContent(801L);

        assertThat(content.bytes()).containsExactly(1, 2, 3);
        assertThat(content.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("代理预览：内容 404 折算为材料不存在")
    void contentMissingIsNotFound() {
        server.expect(requestTo("http://127.0.0.1:8085/file/internal/801/content"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.loadContent(801L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在或已删除");
    }
}
