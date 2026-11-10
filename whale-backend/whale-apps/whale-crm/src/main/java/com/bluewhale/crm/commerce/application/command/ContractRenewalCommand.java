package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 合同续约标记命令。
 *
 * @author BlueWhale
 */
@Data
public class ContractRenewalCommand {

    /** 续约状态：PENDING / RENEWED / NOT_RENEWED */
    @NotBlank
    private String renewalStatus;

    /** 续约负责人 */
    private Long renewalOwnerId;

    /** 续约备注 */
    private String renewalRemark;
}
