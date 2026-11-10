package com.bluewhale.crm.commerce.application.view;

import com.bluewhale.crm.commerce.domain.model.ContractLedger;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同台账视图。
 *
 * @author BlueWhale
 */
@Data
public class ContractView {

    /** 主键 */
    private Long id;

    /** 合同服务合同 ID */
    private Long contractId;

    /** 合同编号 */
    private String contractNo;

    /** 客户 ID */
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

    /** 距到期天数（负数表示已逾期） */
    private Long daysToExpire;

    /** 是否已到期 */
    private Boolean expired;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 领域模型转视图。
     *
     * @param ledger 台账
     * @return 视图
     */
    public static ContractView from(ContractLedger ledger) {
        ContractView view = new ContractView();
        view.setId(ledger.getId());
        view.setContractId(ledger.getContractId());
        view.setContractNo(ledger.getContractNo());
        view.setCustomerId(ledger.getCustomerId());
        view.setTitle(ledger.getTitle());
        view.setCategory(ledger.getCategory());
        view.setAmount(ledger.getAmount());
        view.setCurrency(ledger.getCurrency());
        view.setStatus(ledger.getStatus());
        view.setEffectiveDate(ledger.getEffectiveDate());
        view.setExpireDate(ledger.getExpireDate());
        view.setRenewalStatus(ledger.getRenewalStatus());
        view.setRenewalOwnerId(ledger.getRenewalOwnerId());
        view.setRenewalRemark(ledger.getRenewalRemark());
        view.setRenewalAt(ledger.getRenewalAt());
        view.setLastSyncAt(ledger.getLastSyncAt());
        view.setOwnerId(ledger.getOwnerId());
        view.setDeptId(ledger.getDeptId());
        view.setRemark(ledger.getRemark());
        view.setDaysToExpire(ledger.daysToExpire());
        view.setExpired(ledger.expired());
        view.setCreateTime(ledger.getCreateTime());
        view.setUpdateTime(ledger.getUpdateTime());
        return view;
    }
}
