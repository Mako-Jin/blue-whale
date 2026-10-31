package com.bluewhale.music.application.service;

/**
 * 歌曲媒体资产校验（M5-2 上传白名单与大小上限）。
 *
 * @author BlueWhale
 */
public interface SongAssetValidator {

    /**
     * 校验音频资产：存在、可用、大小不超上限、MIME 命中白名单。
     *
     * @param audioFileId 音频文件 ID
     * @param tenantId 租户 ID
     */
    void assertAudio(Long audioFileId, Long tenantId);

    /**
     * 校验封面资产（未提供封面时直接放行）。
     *
     * @param coverFileId 封面文件 ID，可为 {@code null}
     * @param tenantId 租户 ID
     */
    void assertCover(Long coverFileId, Long tenantId);
}
