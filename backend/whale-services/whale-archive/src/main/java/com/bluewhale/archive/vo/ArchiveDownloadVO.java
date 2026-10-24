package com.bluewhale.archive.vo;

import lombok.Data;

/**
 * 档案文件下载载荷：文件字节与响应头所需的元数据。
 *
 * @author BlueWhale
 */
@Data
public class ArchiveDownloadVO {

    /** 下载文件名 */
    private String fileName;

    /** MIME 类型 */
    private String contentType;

    /** 文件内容 */
    private byte[] content;
}
