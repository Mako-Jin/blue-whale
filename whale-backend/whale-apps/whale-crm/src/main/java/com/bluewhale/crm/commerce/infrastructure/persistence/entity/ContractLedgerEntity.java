package com.bluewhale.crm.commerce.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同台账投影实体，对应表 {@code crm_contract}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_contract")
public class ContractLedgerEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 合同服务合同 ID */
    private Long contractId;

    /** 合同编号 */
    private String contractNo;

    /** CRM 客户 ID */
    private Long customerId;

    /** 合同标题 */
    private String title;

    /** 合同类别 */
    private String category;

    /** 合同金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 合同状态 */
    private String status;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 到期日期 */
    private LocalDate expireDate;

    /** 弱关联业务类型 */
    private String bizType;

    /** 弱关联业务 ID */
    private String bizId;

    /** 续约状态 */
    private String renewalStatus;

    /** 续约负责人 */
    private Long renewalOwnerId;

    /** 续约备注 */
    private String renewalRemark;

    /** 续约标记时间 */
    private LocalDateTime renewalAt;

    /** 最近同步时间 */
    private LocalDateTime lastSyncAt;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 备注 */
    private String remark;
}
