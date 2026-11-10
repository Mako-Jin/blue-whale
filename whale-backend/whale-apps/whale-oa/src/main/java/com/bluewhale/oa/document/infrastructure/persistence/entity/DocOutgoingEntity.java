package com.bluewhale.oa.document.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发文实体，对应表 {@code oa_doc_outgoing}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_doc_outgoing")
public class DocOutgoingEntity extends BaseEntity {

    private String docNo;

    private String title;

    private String docType;

    private Integer securityLevel;

    private Integer urgency;

    private Long templateId;

    private String templateName;

    private Long drafterId;

    private String drafterName;

    private Long deptId;

    private String mainRecipients;

    private String copyRecipients;

    private String content;

    private String signer;

    private LocalDate signDate;

    private LocalDateTime issuedAt;

    private String status;

    private Long approvalInstanceId;

    private String archiveItemId;

    private String remark;
}
