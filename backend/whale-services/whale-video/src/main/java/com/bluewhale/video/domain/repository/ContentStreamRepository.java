package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.VideoStream;

/**
 * 视频内容读取端口（实现方直连 {@code whale-file} 内网内容接口）。
 *
 * <p>文件 ID 只在本端口内出现，不得透传到应用层视图。</p>
 *
 * @author BlueWhale
 */
public interface ContentStreamRepository {

    /**
     * 打开视频内容流，Range / If-Range 原样透传。
     *
     * @param fileId 视频文件 ID
     * @param tenantId 租户 ID
     * @param rangeHeader 请求的 {@code Range} 头
     * @param ifRangeHeader 请求的 {@code If-Range} 头
     * @return 内容流（200 / 206 / 416）
     */
    VideoStream open(Long fileId, Long tenantId, String rangeHeader, String ifRangeHeader);

    /**
     * 打开图片内容流（封面 / 海报 / 背景图代理，透传 Range）。
     *
     * @param fileId 图片文件 ID
     * @param tenantId 租户 ID
     * @param rangeHeader 请求的 {@code Range} 头
     * @param ifRangeHeader 请求的 {@code If-Range} 头
     * @return 内容流（200 / 206 / 416）
     */
    VideoStream openImage(Long fileId, Long tenantId, String rangeHeader, String ifRangeHeader);

    /**
     * 读取文件头字节（用于 magic bytes 校验）。
     *
     * @param fileId 文件 ID
     * @param tenantId 租户 ID
     * @param length 读取字节数上限
     * @return 文件头字节；读取失败返回空数组
     */
    byte[] readHead(Long fileId, Long tenantId, int length);
}
