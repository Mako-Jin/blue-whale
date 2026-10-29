package com.bluewhale.doc.application.service;

/**
 * 附件内容代理结果。
 *
 * @param content     文件字节
 * @param contentType MIME 类型
 * @param filename    原始文件名
 * @author BlueWhale
 */
public record DocAttachmentContent(byte[] content, String contentType, String filename) {
}
