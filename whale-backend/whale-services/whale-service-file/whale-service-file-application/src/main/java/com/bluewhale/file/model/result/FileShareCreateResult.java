package com.bluewhale.file.model.result;

import java.time.LocalDateTime;

/**
 * 创建分享结果。
 *
 * @param id 分享ID
 * @param fileId 被分享的文件ID
 * @param token 分享令牌（前端据此拼接访问链接）
 * @param expireTime 失效时间，可为 {@code null}
 * @param maxDownloads 下载次数上限，可为 {@code null}
 * @param hasPassword 是否设置了分享密码
 * @author BlueWhale
 */
public record FileShareCreateResult(Long id, Long fileId, String token, LocalDateTime expireTime,
                                    Integer maxDownloads, boolean hasPassword) {
}
