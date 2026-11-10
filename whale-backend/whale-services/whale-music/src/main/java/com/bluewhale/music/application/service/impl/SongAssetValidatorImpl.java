package com.bluewhale.music.application.service.impl;

import com.bluewhale.music.application.service.SongAssetValidator;
import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.FileAsset;
import com.bluewhale.music.domain.repository.FileAssetPort;
import com.bluewhale.music.domain.service.UploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 歌曲媒体资产校验实现：把配置项与文件元数据交给领域策略判定。
 *
 * <p>失败即拒绝入库，不因文件服务不可用而降级放行（fail-closed）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SongAssetValidatorImpl implements SongAssetValidator {

    private final FileAssetPort fileAssetPort;

    private final MusicProperties musicProperties;

    @Override
    public void assertAudio(Long audioFileId, Long tenantId) {
        UploadPolicy.assertAudio(require(audioFileId, tenantId), musicProperties.getAudioFormats(),
                musicProperties.getUploadMaxBytes());
    }

    @Override
    public void assertCover(Long coverFileId, Long tenantId) {
        if (coverFileId == null || coverFileId <= 0) {
            return;
        }
        UploadPolicy.assertCover(require(coverFileId, tenantId), musicProperties.getCoverFormats(),
                musicProperties.getUploadMaxBytes());
    }

    /**
     * 读取文件元数据，不存在按上传文件非法处理。
     *
     * @param fileId 文件 ID
     * @param tenantId 租户 ID
     * @return 文件元数据
     */
    private FileAsset require(Long fileId, Long tenantId) {
        return fileAssetPort.find(fileId, tenantId)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.UPLOAD_FILE_INVALID));
    }
}
