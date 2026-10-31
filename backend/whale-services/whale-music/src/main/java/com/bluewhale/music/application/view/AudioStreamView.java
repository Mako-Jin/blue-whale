package com.bluewhale.music.application.view;

import lombok.Data;

import java.io.InputStream;

/**
 * 音频流视图（接口层据此构建 HTTP 响应，不暴露文件 ID）。
 *
 * @author BlueWhale
 */
@Data
public class AudioStreamView {

    /** HTTP 状态码：200 / 206 / 416 */
    private int status;

    /** 响应字节数 */
    private long contentLength;

    /** 分段响应 {@code Content-Range}，全量为 {@code null} */
    private String contentRange;

    /** 越界时的文件总字节数（仅 416 使用） */
    private long contentRangeTotal;

    /** MIME 类型 */
    private String contentType;

    /** 文件 ETag */
    private String etag;

    /** 内容流；416 时为 {@code null} */
    private InputStream body;
}
