package com.bluewhale.file.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件版本响应。
 *
 * @author BlueWhale
 */
@Data
public class FileVersionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private Integer version;

    /** 是否为版本组内当前最新版本；已删除版本恒为 {@code false} */
    private Boolean latest;

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
