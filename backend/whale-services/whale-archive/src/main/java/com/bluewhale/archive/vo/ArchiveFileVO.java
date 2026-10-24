package com.bluewhale.archive.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 档案文件引用视图。
 *
 * <p>文件二进制仍存放于 {@code whale-file}，此处仅返回引用与展示冗余字段。</p>
 *
 * @author BlueWhale
 */
@Data
public class ArchiveFileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 引用ID */
    private String id;

    /** 文件ID（whale-file 的 file_info.id） */
    private String fileId;

    /** 文件角色：ORIGINAL / ATTACHMENT / SNAPSHOT */
    private String fileRole;

    /** 原始文件名 */
    private String originalName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** 文件 MD5 */
    private String md5;

    /** 排序号 */
    private Integer sort;
}
