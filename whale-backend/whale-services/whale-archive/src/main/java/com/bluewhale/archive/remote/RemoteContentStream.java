package com.bluewhale.archive.remote;

import lombok.Data;

import java.io.InputStream;

/**
 * 文件服务内容接口的流式响应：状态码、Range 相关响应头与内容流。
 *
 * <p>与下载用的 {@code byte[]} 载荷不同，本对象承载未缓冲的输入流，
 * 供在线预览 / 播放时透传 HTTP Range，避免大文件整体读入堆内存。</p>
 *
 * @author BlueWhale
 */
@Data
public class RemoteContentStream {

    /** HTTP 状态码：200 全量 / 206 分段 / 416 越界 */
    private int status;

    /** 响应 MIME 类型 */
    private String contentType;

    /** 响应内容长度；未知时为 -1 */
    private long contentLength = -1L;

    /** 分段响应的 {@code Content-Range}（如 {@code bytes 0-1023/2048}） */
    private String contentRange;

    /** 越界响应中 {@code Content-Range} 携带的文件总字节数 */
    private long contentRangeTotal;

    /** 文件 ETag */
    private String etag;

    /** 内容流；416 越界时为空 */
    private InputStream body;
}
