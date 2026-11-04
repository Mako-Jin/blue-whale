package com.bluewhale.pdm.document.support;

/**
 * 图文档下载内容
 *
 * @param content     文件字节
 * @param fileName    文件名
 * @param contentType 内容类型
 * @author BlueWhale
 */
public record PdmDocDownload(byte[] content, String fileName, String contentType) {
}
