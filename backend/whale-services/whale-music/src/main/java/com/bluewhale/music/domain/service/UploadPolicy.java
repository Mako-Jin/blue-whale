package com.bluewhale.music.domain.service;

import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.FileAsset;

import java.util.List;

/**
 * 上传准入策略（领域服务，M5-2）。
 *
 * <p>只做纯规则判定：文件存在且可用、字节数不超上限、MIME 类型命中白名单；
 * 白名单与上限由应用层从配置传入，领域层不感知配置来源。</p>
 *
 * @author BlueWhale
 */
public final class UploadPolicy {

    /**
     * 私有构造器：工具类禁止实例化。
     */
    private UploadPolicy() {
    }

    /**
     * 校验音频资产。
     *
     * @param asset 文件元数据，可为 {@code null}
     * @param allowedFormats 允许的音频 MIME 类型
     * @param maxBytes 单文件字节上限
     */
    public static void assertAudio(FileAsset asset, List<String> allowedFormats, long maxBytes) {
        assertAsset(asset, allowedFormats, maxBytes, MusicErrorCode.AUDIO_FORMAT_NOT_ALLOWED);
    }

    /**
     * 校验封面图片资产。
     *
     * @param asset 文件元数据，可为 {@code null}
     * @param allowedFormats 允许的图片 MIME 类型
     * @param maxBytes 单文件字节上限
     */
    public static void assertCover(FileAsset asset, List<String> allowedFormats, long maxBytes) {
        assertAsset(asset, allowedFormats, maxBytes, MusicErrorCode.COVER_FORMAT_NOT_ALLOWED);
    }

    /**
     * 通用资产校验：可用性 → 大小 → 类型（顺序保证错误码稳定可预期）。
     *
     * @param asset 文件元数据
     * @param allowedFormats 允许的 MIME 类型
     * @param maxBytes 字节上限
     * @param formatErrorCode 类型非法错误码
     */
    private static void assertAsset(FileAsset asset, List<String> allowedFormats, long maxBytes,
                                    int formatErrorCode) {
        if (asset == null || asset.fileId() == null || !asset.usable() || asset.sizeBytes() <= 0) {
            throw MusicErrorCode.exception(MusicErrorCode.UPLOAD_FILE_INVALID);
        }
        if (maxBytes > 0 && asset.sizeBytes() > maxBytes) {
            throw MusicErrorCode.exception(MusicErrorCode.UPLOAD_FILE_TOO_LARGE);
        }
        if (!asset.hasType(allowedFormats)) {
            throw MusicErrorCode.exception(formatErrorCode);
        }
    }
}
