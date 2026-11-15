package com.bluewhale.file.model.response;

import com.bluewhale.file.model.result.FileShareResult;

import java.time.LocalDateTime;

/**
 * 分享链接列表响应。
 *
 * @param id 分享ID
 * @param fileId 被分享的文件ID
 * @param fileName 创建时的原始文件名快照
 * @param token 分享令牌
 * @param expireTime 失效时间，可为 {@code null}
 * @param maxDownloads 下载次数上限，可为 {@code null}
 * @param downloadCount 已下载次数
 * @param status 状态码：1 有效 / 0 已撤销
 * @param createTime 创建时间
 * @author BlueWhale
 */
public record FileShareResponse(Long id, Long fileId, String fileName, String token, LocalDateTime expireTime,
                                Integer maxDownloads, int downloadCount, int status, LocalDateTime createTime) {

    /**
     * 由应用层结果构造响应
     *
     * @param result 分享结果
     * @return 分享响应
     */
    public static FileShareResponse from(FileShareResult result) {
        return new FileShareResponse(result.id(), result.fileId(), result.fileName(), result.token(),
                result.expireTime(), result.maxDownloads(), result.downloadCount(), result.status(),
                result.createTime());
    }
}
