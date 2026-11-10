package com.bluewhale.lms.cert.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 证书实体，对应表 {@code lr_certificate}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_certificate")
public class CertificateEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 证书编号（租户内唯一） */
    private String certNo;

    /** 模板 ID */
    private Long templateId;

    /** 证书名称快照 */
    private String templateName;

    /** 类别快照 */
    private String category;

    /** 持证人用户 ID */
    private Long userId;

    /** 持证人用户名 */
    private String username;

    /** 来源类型 */
    private String sourceType;

    /** 来源业务 ID */
    private Long sourceId;

    /** 来源业务标题快照 */
    private String sourceTitle;

    /** 发证年份 */
    private Integer issueYear;

    /** 当年流水号 */
    private Integer seqNo;

    /** 发证时间 */
    private LocalDateTime issuedAt;

    /** 有效期起 */
    private LocalDateTime validFrom;

    /** 有效期止 */
    private LocalDateTime validTo;

    /** 状态 */
    private String status;

    /** 撤销时间 */
    private LocalDateTime revokedAt;

    /** 撤销人 */
    private Long revokedBy;

    /** 撤销原因 */
    private String revokeReason;

    /** 换发来源证书 ID */
    private Long renewFromId;

    /** 备注 */
    private String remark;
}
