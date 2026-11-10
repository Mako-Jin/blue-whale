package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.AudioStream;

/**
 * 音频内容提供方端口（由 whale-file 内网内容接口实现）。
 *
 * <p>音乐服务只按文件 ID 读取音频内容，向客户端始终以 {@code /music/stream}
 * 票据代理暴露，不泄露文件 ID。</p>
 *
 * @author BlueWhale
 */
public interface AudioContentRepository {

    /**
     * 读取音频内容，支持单段 Range。
     *
     * @param fileId 音频文件 ID
     * @param tenantId 租户 ID（文件服务内网调用留痕）
     * @param rangeHeader 请求的 {@code Range} 头，可为 {@code null}
     * @param ifRangeHeader 请求的 {@code If-Range} 头，可为 {@code null}
     * @return 音频内容流
     */
    AudioStream open(Long fileId, Long tenantId, String rangeHeader, String ifRangeHeader);
}
