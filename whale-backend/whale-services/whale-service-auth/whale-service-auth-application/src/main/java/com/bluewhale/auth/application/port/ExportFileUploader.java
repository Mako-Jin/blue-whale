package com.bluewhale.auth.application.port;

import java.util.Map;

/**
 * 导出文件上传端口（权限中心扩展 M4）：超限导出上传 whale-file 并返回文件引用。
 *
 * @author BlueWhale
 */
public interface ExportFileUploader {

    /**
     * 上传导出文件到 whale-file 内网接口
     *
     * @param content  文件字节内容
     * @param fileName 上传文件名
     * @return 上传结果（含 {@code objectKey}/{@code bucket}/{@code fileSize} 等）
     */
    Map<String, Object> upload(byte[] content, String fileName);
}
