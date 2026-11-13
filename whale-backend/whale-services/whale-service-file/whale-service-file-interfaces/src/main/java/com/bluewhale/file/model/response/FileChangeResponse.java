package com.bluewhale.file.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件变更响应（内网变更流）。
 *
 * @author BlueWhale
 */
@Data
public class FileChangeResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String fileName;
    private String originalName;
    private String bucket;
    private String objectKey;
    private Long fileSize;
    private String contentType;
    private String md5;
    private Integer status;
    private Long uploaderId;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
