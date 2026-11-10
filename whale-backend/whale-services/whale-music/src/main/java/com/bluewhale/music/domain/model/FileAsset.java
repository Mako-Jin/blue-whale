package com.bluewhale.music.domain.model;

import java.util.List;
import java.util.Locale;

/**
 * 文件资产元数据（whale-file 内部元数据接口的领域投影）。
 *
 * <p>音乐服务不持有存储凭据，只读取 MIME 类型、字节数与可用状态，
 * 用于上传白名单与大小上限校验（M5-2）。</p>
 *
 * @param fileId 文件 ID
 * @param contentType 文件 MIME 类型，可为 {@code null}
 * @param sizeBytes 文件字节数
 * @param uploaderId 上传者用户 ID，可为 {@code null}
 * @param usable 文件是否可正常使用（未禁用、未归档锁定、未逻辑删除）
 * @author BlueWhale
 */
public record FileAsset(Long fileId, String contentType, long sizeBytes, Long uploaderId, boolean usable) {

    /**
     * 判断文件 MIME 类型是否落在白名单内（忽略大小写与 {@code ;} 后的参数）。
     *
     * @param allowedFormats 允许的 MIME 类型集合
     * @return 命中返回 {@code true}
     */
    public boolean hasType(List<String> allowedFormats) {
        if (contentType == null || contentType.isBlank() || allowedFormats == null || allowedFormats.isEmpty()) {
            return false;
        }
        String actual = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = actual.indexOf(';');
        if (semicolon >= 0) {
            actual = actual.substring(0, semicolon).trim();
        }
        String expected = actual;
        return allowedFormats.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .anyMatch(item -> item.equals(expected));
    }
}
