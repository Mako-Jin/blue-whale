package com.bluewhale.crm.commerce.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同台账投影。
 *
 * <p>仅保存 whale-contract 的台账字段（编号 / 金额 / 状态 / 生效与到期），
 * 不复制合同正文、模板、相对方与签署信息；合同变更以投影同步覆盖为准，
 * 本地只额外维护续约标记。</p>
 *
 * @author BlueWhale
 */
@Data
public class ContractLedger {

    /** 主键 */
    private Long id;

    /** 合同服务合同 ID */
    private Long contractId;

    /** 合同编号（投影） */
    private String contractNo;

    /** CRM 客户 ID */
    private Long customerId;

    /** 合同标题（投影） */
    private String title;

    /** 合同类别（投影） */
    private String category;

    /** 合同金额（投影） */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 合同状态（以合同服务为准） */
    private String status;

    /** 生效日期（投影） */
    private LocalDate effectiveDate;

    /** 到期日期（投影） */
    private LocalDate expireDate;

    /** 弱关联业务类型 */
    private String bizType;

    /** 弱关联业务 ID */
    private String bizId;

    /** 续约状态：PENDING / RENEWED / NOT_RENEWED */
    private String renewalStatus;

    /** 续约负责人 */
    private Long renewalOwnerId;

    /** 续约备注 */
    private String renewalRemark;

    /** 续约标记时间 */
    private LocalDateTime renewalAt;

    /** 最近同步时间 */
    private LocalDateTime lastSyncAt;

    /** 负责人（数据范围归属人） */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 是否已到期（含当天）。
     *
     * @return 到期返回 true
     */
    public boolean expired() {
        return expireDate != null && !expireDate.isAfter(LocalDate.now());
    }

    /**
     * 距到期天数；无到期日返回 {@code null}。
     *
     * @return 天数，负数表示已逾期
     */
    public Long daysToExpire() {
        return expireDate == null ? null : java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expireDate);
    }
}
