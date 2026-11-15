package com.bluewhale.file.model.response;

import com.bluewhale.file.model.result.FileShareCreateResult;

import java.time.LocalDateTime;

/**
 * 创建分享链接响应。
 *
 * @param id 分享ID
 * @param fileId 被分享的文件ID
 * @param token 分享令牌（前端据此拼接访问链接）
 * @param expireTime 失效时间，可为 {@code null}
 * @param maxDownloads 下载次数上限，可为 {@code null}
 * @param hasPassword 是否设置了分享密码
 * @author BlueWhale
 */
public record FileShareCreateResponse(Long id, Long fileId, String token, LocalDateTime expireTime,
                                      Integer maxDownloads, boolean hasPassword) {

    /**
     * 由应用层结果构造响应
     *
     * @param result 创建结果
     * @return 创建分享响应
     */
    public static FileShareCreateResponse from(FileShareCreateResult result) {
        return new FileShareCreateResponse(result.id(), result.fileId(), result.token(), result.expireTime(),
                result.maxDownloads(), result.hasPassword());
    }
}
