package com.bluewhale.crm.service.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * SLA 策略保存命令。
 *
 * @author BlueWhale
 */
@Data
public class SlaPoliciesCommand {

    /** 三档策略 */
    @NotEmpty
    @Valid
    private List<SlaPolicyCommand> policies;
}
