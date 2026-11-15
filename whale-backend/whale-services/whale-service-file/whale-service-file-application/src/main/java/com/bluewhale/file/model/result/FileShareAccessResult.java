package com.bluewhale.file.model.result;

import java.time.LocalDateTime;

/**
 * 分享链接访问结果。
 *
 * @param file 文件元数据；需要密码且未提供时为空（不泄露文件名与大小）
 * @param needPassword 是否需要密码
 * @param expireTime 失效时间，可为 {@code null}
 * @param maxDownloads 下载次数上限，可为 {@code null}
 * @param downloadCount 已下载次数
 * @author BlueWhale
 */
public record FileShareAccessResult(FileInfoResult file, boolean needPassword, LocalDateTime expireTime,
                                    Integer maxDownloads, int downloadCount) {
}
