package com.bluewhale.file.domain.share.model;

import java.time.LocalDateTime;

/**
 * 分享链接列表投影（只读）。
 *
 * @param id 分享ID
 * @param fileId 被分享的文件ID
 * @param fileName 创建时的原始文件名快照
 * @param token 分享令牌
 * @param expireTime 失效时间，可为 {@code null}
 * @param maxDownloads 下载次数上限，可为 {@code null}
 * @param downloadCount 已下载次数
 * @param status 状态码：1 有效 / 0 已撤销
 * @param creatorId 创建人用户 ID
 * @param createTime 创建时间
 * @author BlueWhale
 */
public record FileShareItem(Long id, Long fileId, String fileName, String token, LocalDateTime expireTime,
                            Integer maxDownloads, int downloadCount, int status, Long creatorId,
                            LocalDateTime createTime) {
}
