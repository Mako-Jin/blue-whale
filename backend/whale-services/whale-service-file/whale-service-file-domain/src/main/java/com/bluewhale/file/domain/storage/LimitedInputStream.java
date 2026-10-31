package com.bluewhale.file.domain.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 限定读取长度的输入流：剩余字节耗尽后统一返回 {@code -1}，用于支撑 HTTP Range 分段响应。
 *
 * <p>仅依赖 JDK，供 {@link FileStorage} 的默认区间读取实现使用；不支持标记与重置。</p>
 *
 * @author BlueWhale
 */
final class LimitedInputStream extends FilterInputStream {

    /** 尚未读取的允许字节数 */
    private long remaining;

    /**
     * 以底层内容流与读取上限构造。
     *
     * @param in 底层内容流
     * @param limit 最大可读取字节数，必须大于 0
     */
    LimitedInputStream(InputStream in, long limit) {
        super(in);
        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int value = in.read();
        if (value >= 0) {
            remaining--;
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int read = in.read(buffer, offset, (int) Math.min(length, remaining));
        if (read > 0) {
            remaining -= read;
        }
        return read;
    }

    @Override
    public long skip(long count) throws IOException {
        long skipped = in.skip(Math.min(count, remaining));
        remaining -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(in.available(), remaining);
    }

    @Override
    public boolean markSupported() {
        return false;
    }
}
