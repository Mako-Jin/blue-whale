package com.bluewhale.video.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传声明签署记录（同一用户同一版本只记录一次）。
 *
 * @author BlueWhale
 */
@Data
public class UploadDeclaration {

    /** 主键 */
    private Long id;

    /** 签署用户 ID */
    private Long userId;

    /** 声明版本号 */
    private String declarationVersion;

    /** 签署时间 */
    private LocalDateTime acceptedAt;

    /** 客户端 IP */
    private String clientIp;

    /** 客户端标识 */
    private String userAgent;

    /** 内容来源 */
    private CopyrightSource contentSource;
}
