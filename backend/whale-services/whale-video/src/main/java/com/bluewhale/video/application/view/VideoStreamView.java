package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.InputStream;
import java.io.Serializable;

/**
 * 视频流视图（Range 透传结果，供接口层组装响应头）。
 *
 * @author BlueWhale
 */
@Data
public class VideoStreamView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** HTTP 状态码：200 / 206 / 416 */
    private int status;

    /** 本次响应字节数（未知为 -1） */
    private long contentLength;

    /** 响应 {@code Content-Range}（全量时为 {@code null}） */
    private String contentRange;

    /** 越界时的文件总字节数（仅 416 使用） */
    private long contentRangeTotal;

    /** MIME 类型 */
    private String contentType;

    /** 文件 ETag */
    private String etag;

    /** 内容流（416 时为 {@code null}） */
    private InputStream body;
}
