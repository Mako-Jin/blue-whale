package com.bluewhale.common.log.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 限长缓存请求包装器（tee 透传方案，REQ-SEC-006 A4）。
 *
 * <p>从真实请求流读取的字节<b>全量透传</b>给下游，同时镜像写入内部缓存；
 * 一旦镜像长度达到 {@code maxBodyLength} 即停止镜像，<b>但继续全量透传</b>——
 * 绝不截断真实请求体（有意替换 Spring {@code ContentCachingRequestWrapper} 的缓冲式实现）。</p>
 *
 * <p>底层请求流至多获取一次：首次调用 {@link #getInputStream()} / {@link #getReader()} 时缓存 tee 流，
 * 保证镜像与下游消费的是同一条流。</p>
 *
 * @author BlueWhale
 */
public class LimitedCachingRequestWrapper extends HttpServletRequestWrapper {

    /** 请求体默认编码 */
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /** 镜像缓存上限（字节），达到后停止镜像但继续透传 */
    private final int maxBodyLength;

    /** 已读字节镜像 */
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

    /** tee 包装后的请求流（底层流只取一次） */
    private ServletInputStream inputStream;

    /** 字符读取器（按请求编码包装 tee 流） */
    private BufferedReader reader;

    /**
     * 构造限长缓存请求包装器
     *
     * @param request 原始请求
     * @param maxBodyLength 镜像缓存上限（字节）
     */
    public LimitedCachingRequestWrapper(HttpServletRequest request, int maxBodyLength) {
        super(request);
        this.maxBodyLength = maxBodyLength;
    }

    /**
     * 获取 tee 请求流：字节全量透传 + 镜像入限长缓存
     *
     * @return 包装后的请求流
     * @throws IOException 读取底层流失败
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (inputStream == null) {
            inputStream = new LimitedCachingInputStream(super.getInputStream(), captured, maxBodyLength);
        }
        return inputStream;
    }

    /**
     * 获取字符读取器（按请求编码包装 tee 流，底层流仅取一次）
     *
     * @return 包装后的 BufferedReader
     * @throws IOException 读取底层流失败
     */
    @Override
    public BufferedReader getReader() throws IOException {
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(getInputStream(), resolveCharset()));
        }
        return reader;
    }

    /**
     * 获取镜像缓存的请求体文本
     *
     * @return UTF-8 请求体文本；未缓存到任何字节时返回 {@code null}
     */
    public String getCapturedBody() {
        if (captured.size() == 0) {
            return null;
        }
        return captured.toString(DEFAULT_CHARSET);
    }

    /**
     * 解析请求体编码，未声明或非法时回落 UTF-8
     *
     * @return 请求体字符集
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
     * tee 输入流：字节全量透传给调用方，同时镜像写入缓存（超限停镜像不截断透传）
     *
     * @author BlueWhale
     */
    private static final class LimitedCachingInputStream extends ServletInputStream {

        /** 真实请求流 */
        private final ServletInputStream delegate;

        /** 已读字节镜像 */
        private final ByteArrayOutputStream mirror;

        /** 镜像上限（字节） */
        private final int maxBodyLength;

        /**
         * 构造 tee 流
         *
         * @param delegate 真实请求流
         * @param mirror 已读字节镜像
         * @param maxBodyLength 镜像上限（字节）
         */
        LimitedCachingInputStream(ServletInputStream delegate, ByteArrayOutputStream mirror, int maxBodyLength) {
            this.delegate = delegate;
            this.mirror = mirror;
            this.maxBodyLength = maxBodyLength;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int read = read(single, 0, 1);
            return read == -1 ? -1 : single[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = delegate.read(b, off, len);
            if (read > 0) {
                mirror(b, off, read);
            }
            return read;
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        /**
         * 镜像写入：到达上限即停止镜像，不影响调用方已读取的数据
         *
         * @param buf 已读字节缓冲
         * @param off 起始偏移
         * @param len 已读长度
         */
        private void mirror(byte[] buf, int off, int len) {
            int remaining = maxBodyLength - mirror.size();
            if (remaining <= 0) {
                return;
            }
            mirror.write(buf, off, Math.min(remaining, len));
        }
    }
}
