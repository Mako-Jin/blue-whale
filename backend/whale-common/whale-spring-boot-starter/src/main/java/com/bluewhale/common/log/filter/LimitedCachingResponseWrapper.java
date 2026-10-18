package com.bluewhale.common.log.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 限长缓存响应包装器（tee 透传方案，REQ-SEC-006 A4）。
 *
 * <p>写入响应流的字节<b>全量直达客户端</b>，同时镜像写入内部缓存；一旦镜像长度达到
 * {@code maxBodyLength} 即停止镜像，<b>但不截断透传</b>。因字节已直达客户端，
 * 无需 {@code copyBodyToResponse()}（有意偏离方案参考实现，避免缓冲式回写截断响应体）。</p>
 *
 * @author BlueWhale
 */
public class LimitedCachingResponseWrapper extends HttpServletResponseWrapper {

    /** 响应体默认编码 */
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /** 镜像缓存上限（字节），达到后停止镜像但继续透传 */
    private final int maxBodyLength;

    /** 已写字节镜像 */
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

    /** tee 包装后的响应流（底层流只取一次） */
    private ServletOutputStream outputStream;

    /** 字符输出器（按响应编码包装 tee 流） */
    private PrintWriter writer;

    /**
     * 构造限长缓存响应包装器
     *
     * @param response 原始响应
     * @param maxBodyLength 镜像缓存上限（字节）
     */
    public LimitedCachingResponseWrapper(HttpServletResponse response, int maxBodyLength) {
        super(response);
        this.maxBodyLength = maxBodyLength;
    }

    /**
     * 获取 tee 响应流：字节全量直达客户端 + 镜像入限长缓存
     *
     * @return 包装后的响应流
     * @throws IOException 读取底层流失败
     */
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            if (writer != null) {
                throw new IllegalStateException("getWriter() has already been called for this response");
            }
            outputStream = new LimitedCachingOutputStream(super.getOutputStream(), captured, maxBodyLength);
        }
        return outputStream;
    }

    /**
     * 获取字符输出器（按响应编码包装 tee 流）
     *
     * @return 包装后的 PrintWriter
     * @throws IOException 读取底层流失败
     */
    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), resolveCharset()), true);
        }
        return writer;
    }

    /**
     * 获取镜像缓存的响应体文本
     *
     * @return UTF-8 响应体文本；未缓存到任何字节时返回 {@code null}
     */
    public String getCapturedBody() {
        if (captured.size() == 0) {
            return null;
        }
        return captured.toString(DEFAULT_CHARSET);
    }

    /**
     * 解析响应体编码，未声明或非法时回落 UTF-8
     *
     * @return 响应体字符集
     */
    private Charset resolveCharset() {
        String encoding = getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) {
            return DEFAULT_CHARSET;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            return DEFAULT_CHARSET;
        }
    }

    /**
     * tee 输出流：字节全量直达客户端，同时镜像写入缓存（超限停镜像不截断透传）
     *
     * @author BlueWhale
     */
    private static final class LimitedCachingOutputStream extends ServletOutputStream {

        /** 真实响应流 */
        private final ServletOutputStream delegate;

        /** 已写字节镜像 */
        private final ByteArrayOutputStream mirror;

        /** 镜像上限（字节） */
        private final int maxBodyLength;

        /**
         * 构造 tee 流
         *
         * @param delegate 真实响应流
         * @param mirror 已写字节镜像
         * @param maxBodyLength 镜像上限（字节）
         */
        LimitedCachingOutputStream(ServletOutputStream delegate, ByteArrayOutputStream mirror, int maxBodyLength) {
            this.delegate = delegate;
            this.mirror = mirror;
            this.maxBodyLength = maxBodyLength;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            if (mirror.size() < maxBodyLength) {
                mirror.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            int remaining = maxBodyLength - mirror.size();
            if (remaining > 0) {
                mirror.write(b, off, Math.min(remaining, len));
            }
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            delegate.setWriteListener(writeListener);
        }
    }
}
