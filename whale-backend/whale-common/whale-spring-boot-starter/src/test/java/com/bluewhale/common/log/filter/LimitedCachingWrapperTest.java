package com.bluewhale.common.log.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 限长缓存请求/响应包装器单测（tee 透传方案，REQ-SEC-006 A4 / checklist 8.2）。
 *
 * <p>验证核心不变量：字节<b>全量透传</b>给下游/客户端，镜像入限长缓存；超 {@code maxBodyLength}
 * 后停止镜像但绝不截断透传，{@code getCapturedBody()} 不抛异常。</p>
 *
 * @author BlueWhale
 */
class LimitedCachingWrapperTest {

    /** 镜像上限（字节） */
    private static final int MAX_BODY_LENGTH = 10;

    @Test
    @DisplayName("请求体小于上限：镜像完整且下游读到全量")
    void shouldMirrorFullRequestBodyWhenBelowLimit() throws Exception {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(body);

        LimitedCachingRequestWrapper wrapper = new LimitedCachingRequestWrapper(request, MAX_BODY_LENGTH);
        byte[] downstream = wrapper.getInputStream().readAllBytes();

        assertThat(downstream).isEqualTo(body);
        assertThat(wrapper.getCapturedBody()).isEqualTo("hello");
    }

    @Test
    @DisplayName("请求体超上限：镜像停在上限，但下游仍读到全量且不截断")
    void shouldStopMirroringButKeepFullPassthroughWhenOverLimit() throws Exception {
        byte[] body = "0123456789ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(body);

        LimitedCachingRequestWrapper wrapper = new LimitedCachingRequestWrapper(request, MAX_BODY_LENGTH);
        byte[] downstream = wrapper.getInputStream().readAllBytes();

        assertThat(downstream).isEqualTo(body);
        assertThat(wrapper.getCapturedBody()).hasSize(MAX_BODY_LENGTH);
    }

    @Test
    @DisplayName("请求无内容：镜像为空且 getCapturedBody 返回 null")
    void shouldReturnNullWhenRequestHasNoContent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        LimitedCachingRequestWrapper wrapper = new LimitedCachingRequestWrapper(request, MAX_BODY_LENGTH);
        wrapper.getInputStream().readAllBytes();

        assertThat(wrapper.getCapturedBody()).isNull();
    }

    @Test
    @DisplayName("响应体小于上限：字节全量到达底层流且镜像完整")
    void shouldMirrorFullResponseBodyWhenBelowLimit() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] body = "hi".getBytes(StandardCharsets.UTF_8);

        LimitedCachingResponseWrapper wrapper = new LimitedCachingResponseWrapper(response, MAX_BODY_LENGTH);
        wrapper.getOutputStream().write(body);
        wrapper.getOutputStream().flush();

        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        assertThat(wrapper.getCapturedBody()).isEqualTo("hi");
    }

    @Test
    @DisplayName("响应体超上限：底层流收到全量字节，镜像不超上限")
    void shouldKeepFullResponseBytesOutAndCapMirrorWhenOverLimit() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] body = "0123456789ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8);

        LimitedCachingResponseWrapper wrapper = new LimitedCachingResponseWrapper(response, MAX_BODY_LENGTH);
        wrapper.getOutputStream().write(body);
        wrapper.getOutputStream().flush();

        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        assertThat(wrapper.getCapturedBody()).hasSize(MAX_BODY_LENGTH);
    }
}
