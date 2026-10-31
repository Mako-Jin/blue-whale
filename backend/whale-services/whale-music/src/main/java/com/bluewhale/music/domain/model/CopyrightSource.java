package com.bluewhale.music.domain.model;

import com.bluewhale.music.domain.exception.MusicErrorCode;

import java.util.Locale;

/**
 * 作品上传来源（版权合规留痕，M5-3）。
 *
 * <p>上传者必须声明素材来源，随歌曲持久化并可追溯；非法或缺失统一拒绝，
 * 便于版权争议时回溯「自有 / 授权 / 无版权争议素材」。</p>
 *
 * @author BlueWhale
 */
public enum CopyrightSource {

    /** 本人原创 */
    ORIGINAL,

    /** 已获权利人授权 */
    AUTHORIZED,

    /** 无版权争议素材（公有领域 / 免版权） */
    ROYALTY_FREE;

    /**
     * 解析上传来源：大小写不敏感，缺失与非法取值分别返回不同业务码。
     *
     * @param value 请求中的来源文本
     * @return 上传来源
     */
    public static CopyrightSource parse(String value) {
        if (value == null || value.isBlank()) {
            throw MusicErrorCode.exception(MusicErrorCode.COPYRIGHT_SOURCE_REQUIRED);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw MusicErrorCode.exception(MusicErrorCode.COPYRIGHT_SOURCE_INVALID);
        }
    }
}
