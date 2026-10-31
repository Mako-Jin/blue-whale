package com.bluewhale.music.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版权无争议协议签署记录。
 *
 * <p>同一用户同一协议版本仅一条记录；签署后不可修改，用于版权追溯。</p>
 *
 * @author BlueWhale
 */
@Data
public class CopyrightAgreement {

    /** 主键 */
    private Long id;

    /** 签署用户 ID */
    private Long userId;

    /** 协议版本号 */
    private String agreementVersion;

    /** 签署时间 */
    private LocalDateTime acceptedAt;

    /** 客户端 IP */
    private String clientIp;

    /** 客户端标识 */
    private String userAgent;
}
