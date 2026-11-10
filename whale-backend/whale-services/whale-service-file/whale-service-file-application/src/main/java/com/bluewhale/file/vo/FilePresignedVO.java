package com.bluewhale.file.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 预签名下载 URL 视图对象。
 *
 * @author BlueWhale
 */
@Data
public class FilePresignedVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预签名下载 URL */
    private String url;

    /** 有效期（秒） */
    private Integer expiresSeconds;
}
