package com.bluewhale.file.domain.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * 分片合并结果：合并后的内容流与其校验信息，由调用方负责关闭内容流。
 *
 * @param content 合并后的内容流
 * @param size 合并后的字节数
 * @param contentType 会话登记的 MIME 类型，可为 {@code null}
 * @param md5 合并内容的 MD5 摘要（十六进制小写）
 * @author BlueWhale
 */
public record MergedChunkUpload(InputStream content, long size, String contentType, String md5) implements Closeable {

    /**
     * 关闭合并后的内容流
     *
     * @throws IOException 关闭失败时抛出
     */
    @Override
    public void close() throws IOException {
        content.close();
    }
}
