package com.bluewhale.pdm.document.support;

/**
 * 图文档上传文件（与 Spring Web 解耦）
 *
 * @param content     文件字节
 * @param fileName    文件名
 * @param contentType MIME 类型
 * @param size        字节数
 * @author BlueWhale
 */
public record PdmDocUploadFile(byte[] content, String fileName, String contentType, long size) {
}
