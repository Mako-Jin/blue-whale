package com.bluewhale.music.domain.model;

import java.io.InputStream;

/**
 * 音频内容流（文件服务 Range 响应在音乐服务内的载体）。
 *
 * <p>不暴露文件 ID：上层只拿到状态码、响应头与内容流；{@code status} 为
 * 200 / 206 / 416，416 时 {@code body} 为空并携带 {@code contentRangeTotal}。</p>
 *
 * @param status HTTP 状态码（200 / 206 / 416）
 * @param contentLength 本次响应字节数
 * @param contentRange 响应 {@code Content-Range}，全量时为 {@code null}
 * @param contentRangeTotal 越界时的文件总字节数（仅 416 使用）
 * @param contentType MIME 类型
 * @param etag 文件 ETag
 * @param body 内容流；416 时为 {@code null}
 * @author BlueWhale
 */
public record AudioStream(int status, long contentLength, String contentRange, long contentRangeTotal,
                          String contentType, String etag, InputStream body) {
}
