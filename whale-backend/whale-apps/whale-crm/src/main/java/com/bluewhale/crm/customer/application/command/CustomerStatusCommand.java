package com.bluewhale.crm.customer.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户状态变更命令。
 *
 * @author BlueWhale
 */
@Data
public class CustomerStatusCommand {

    /** 状态 ACTIVE / INACTIVE */
    @NotBlank(message = "{validation.crm.customer-status.status-required}")
    private String status;
}
