package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 申诉材料视图。
 *
 * @author BlueWhale
 */
@Data
public class AppealMaterialVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** whale-file 文件ID */
    private Long fileId;

    /** 文件名 */
    private String fileName;

    /** 文件字节数 */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** 下载地址（网关不剥前缀，前端可直接打开） */
    private String downloadUrl;
}
