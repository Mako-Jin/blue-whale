package com.bluewhale.oa.document.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收文实体，对应表 {@code oa_doc_incoming}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_doc_incoming")
public class DocIncomingEntity extends BaseEntity {

    private String incomingNo;

    private String sourceUnit;

    private String sourceNo;

    private String title;

    private Integer securityLevel;

    private Integer urgency;

    private LocalDate receivedDate;

    private LocalDate dueDate;

    private Long assigneeId;

    private String assigneeName;

    private String instruction;

    private LocalDateTime handledAt;

    private String status;

    private String archiveItemId;

    private String remark;
}
