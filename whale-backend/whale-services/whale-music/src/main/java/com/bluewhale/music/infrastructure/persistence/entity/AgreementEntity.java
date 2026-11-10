package com.bluewhale.music.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版权协议签署实体，对应表 {@code mu_copyright_agreement}（无逻辑删除列）。
 *
 * @author BlueWhale
 */
@Data
@TableName("mu_copyright_agreement")
public class AgreementEntity {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
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
