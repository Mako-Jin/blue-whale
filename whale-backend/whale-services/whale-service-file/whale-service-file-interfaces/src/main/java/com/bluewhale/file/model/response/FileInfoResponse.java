package com.bluewhale.file.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件元数据响应。
 *
 * @author BlueWhale
 */
@Data
public class FileInfoResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String fileName;
    private String originalName;
    private String bucket;
    private String objectKey;
    private Long fileSize;
    private String contentType;
    private String md5;
    private Long uploaderId;
    private LocalDateTime createTime;
}
