package com.bluewhale.file.model.response;

import com.bluewhale.file.model.result.FileShareAccessResult;
import com.bluewhale.file.model.result.FileInfoResult;

import java.time.LocalDateTime;

/**
 * 分享链接访问响应。
 *
 * <p>需要密码且未提供密码时 {@code needPassword=true}，文件名、大小与类型均为空，
 * 避免未授权访客获取文件信息。</p>
 *
 * @param fileName 原始文件名；需要密码时为空
 * @param fileSize 文件字节数；需要密码时为空
 * @param contentType MIME 类型；需要密码时为空
 * @param needPassword 是否需要密码
 * @param expireTime 失效时间，可为 {@code null}
 * @param maxDownloads 下载次数上限，可为 {@code null}
 * @param downloadCount 已下载次数
 * @author BlueWhale
 */
public record FileShareAccessResponse(String fileName, Long fileSize, String contentType, boolean needPassword,
                                      LocalDateTime expireTime, Integer maxDownloads, int downloadCount) {

    /**
     * 由应用层结果构造响应
     *
     * @param result 访问结果
     * @return 访问分享响应
     */
    public static FileShareAccessResponse from(FileShareAccessResult result) {
        FileInfoResult file = result.file();
        return new FileShareAccessResponse(file == null ? null : file.originalName(),
                file == null ? null : file.fileSize(),
                file == null ? null : file.contentType(),
                result.needPassword(), result.expireTime(), result.maxDownloads(), result.downloadCount());
    }
}
