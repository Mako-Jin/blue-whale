package com.bluewhale.crm.customer.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 公海分配命令。
 *
 * @author BlueWhale
 */
@Data
public class PoolAssignCommand {

    /** 新负责人 */
    @NotNull(message = "{validation.crm.pool-assign.owner-id-required}")
    private Long ownerId;

    /** 新归属部门 */
    private Long deptId;

    /** 备注 */
    private String remark;
}
