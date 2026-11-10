package com.bluewhale.oa.administrative.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购申请实体，对应表 {@code oa_purchase_request}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_purchase_request")
public class PurchaseRequestEntity extends BaseEntity {

    private String code;

    private String applicant;

    private Long applicantId;

    private Long deptId;

    private String reason;

    private LocalDate expectedDate;

    private Long supplierId;

    private Long warehouseId;

    private BigDecimal totalAmount;

    private String currency;

    private String status;

    private Long approvalInstanceId;

    private String erpPushStatus;

    private String erpPoNo;

    private String remark;
}
