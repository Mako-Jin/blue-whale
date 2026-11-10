package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传声明签署实体，对应表 {@code vd_upload_declaration}。
 *
 * <p>该表无 {@code update_time} / {@code deleted} 列，故不复用 {@code BaseEntity}；
 * {@code create_time} 由数据库默认值填充。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("vd_upload_declaration")
public class DeclarationEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
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
    private String contentSource;
}
