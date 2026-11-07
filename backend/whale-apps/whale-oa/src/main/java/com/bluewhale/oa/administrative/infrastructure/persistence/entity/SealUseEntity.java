package com.bluewhale.oa.administrative.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用章申请实体，对应表 {@code oa_seal_use}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_seal_use")
public class SealUseEntity extends BaseEntity {

    private String code;

    private String applicant;

    private Long applicantId;

    private Long deptId;

    private Long sealId;

    private String sealName;

    private String reason;

    private Integer fileCount;

    private LocalDateTime useTime;

    private String status;

    private LocalDateTime registeredTime;

    private Long registeredBy;

    private Long approvalInstanceId;

    private String remark;
}
