package com.bluewhale.music.interfaces.controller;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.music.application.service.PlayAppService;
import com.bluewhale.music.application.view.AudioStreamView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 音频流接口单元测试：票据参数、Range 透传与响应头安全约束。
 *
 * <p>直接调用控制器方法，覆盖 200 / 206 / 416 三种状态与禁止缓存响应头。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MusicStreamControllerTest {

    /** 测试租户 */
    private static final Long TENANT_ID = 1L;

    /** 测试用户 */
    private static final String USER_ID = "7";

    /** 测试歌曲 */
    private static final Long SONG_ID = 9L;

    @Mock
    private PlayAppService playAppService;

    @InjectMocks
    private MusicStreamController controller;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 206 分段响应：透出 Content-Range、ETag 与禁止缓存的安全响应头。
     */
    @Test
    @DisplayName("206：分段响应携带 Content-Range / ETag / private no-store / nosniff")
    void shouldReturnPartialContent() {
        AudioStreamView view = viewOf(206, "bytes 0-1023/2048");
        when(playAppService.openStream(eq(SONG_ID), eq("t1"), eq(7L), eq(TENANT_ID), any(), any()))
                .thenReturn(view);
        var response = controller.stream(SONG_ID, "t1", "bytes=0-1023", null, request());
        assertEquals(206, response.getStatusCode().value());
        assertEquals("bytes 0-1023/2048", response.getHeaders().getFirst("Content-Range"));
        assertEquals("private, no-store", response.getHeaders().getFirst("Cache-Control"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("\"md5\"", response.getHeaders().getETag());
    }

    /**
     * 416 越界响应：返回 {@code bytes *}{@code /size} 且无响应体。
     */
    @Test
    @DisplayName("416：越界响应携带 Content-Range: bytes */size 且无响应体")
    void shouldReturnRangeNotSatisfiable() {
        AudioStreamView view = new AudioStreamView();
        view.setStatus(416);
        view.setContentRangeTotal(2048L);
        view.setContentType("audio/mpeg");
        when(playAppService.openStream(any(), any(), any(), any(), any(), any())).thenReturn(view);
        var response = controller.stream(SONG_ID, "t1", "bytes=2048-", null, request());
        assertEquals(416, response.getStatusCode().value());
        assertEquals("bytes */2048", response.getHeaders().getFirst("Content-Range"));
        assertEquals("private, no-store", response.getHeaders().getFirst("Cache-Control"));
        assertNull(response.getBody());
    }

    /**
     * 200 全量响应：无 Content-Range，Content-Length 为文件全长。
     */
    @Test
    @DisplayName("200：全量响应无 Content-Range，长度与元数据一致")
    void shouldReturnFullContent() {
        AudioStreamView view = viewOf(200, null);
        view.setContentLength(2048L);
        when(playAppService.openStream(any(), any(), any(), any(), any(), any())).thenReturn(view);
        var response = controller.stream(SONG_ID, "t1", null, null, request());
        assertEquals(200, response.getStatusCode().value());
        assertNull(response.getHeaders().getFirst("Content-Range"));
        assertEquals("2048", response.getHeaders().getFirst("Content-Length"));
    }

    /**
     * 构造带用户头的请求。
     *
     * @return 请求
     */
    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", USER_ID);
        return request;
    }

    /**
     * 构造音频流视图。
     *
     * @param status 状态码
     * @param contentRange Content-Range
     * @return 视图
     */
    private AudioStreamView viewOf(int status, String contentRange) {
        AudioStreamView view = new AudioStreamView();
        view.setStatus(status);
        view.setContentLength(1024L);
        view.setContentRange(contentRange);
        view.setContentType("audio/mpeg");
        view.setEtag("\"md5\"");
        view.setBody(new ByteArrayInputStream(new byte[1024]));
        return view;
    }
}
