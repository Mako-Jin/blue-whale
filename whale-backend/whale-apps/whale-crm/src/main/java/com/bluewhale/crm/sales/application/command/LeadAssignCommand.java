package com.bluewhale.crm.sales.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 线索分配命令。
 *
 * @author BlueWhale
 */
@Data
public class LeadAssignCommand {

    /** 负责人 */
    @NotNull(message = "{validation.crm.lead-assign.owner-id-required}")
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;
}
