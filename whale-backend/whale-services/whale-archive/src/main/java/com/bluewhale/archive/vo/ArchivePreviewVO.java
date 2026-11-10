package com.bluewhale.archive.vo;

import lombok.Data;

import java.io.InputStream;

/**
 * 档案文件在线预览载荷：文件名、MIME、Range 响应头与流式内容。
 *
 * <p>{@code status} 为下游文件服务返回的 HTTP 状态：200 全量、206 分段、
 * 416 越界（此时 {@code contentRangeTotal} 为文件总字节数，{@code body} 为空）。</p>
 *
 * @author BlueWhale
 */
@Data
public class ArchivePreviewVO {

    /** 预览文件名 */
    private String fileName;

    /** MIME 类型 */
    private String contentType;

    /** 下游 HTTP 状态码 */
    private int status;

    /** 响应内容长度；未知时为 -1 */
    private long contentLength = -1L;

    /** 分段响应的 {@code Content-Range} */
    private String contentRange;

    /** 越界响应对应的文件总字节数 */
    private long contentRangeTotal;

    /** 文件 ETag */
    private String etag;

    /** 内容流；416 越界时为空 */
    private InputStream body;
}
