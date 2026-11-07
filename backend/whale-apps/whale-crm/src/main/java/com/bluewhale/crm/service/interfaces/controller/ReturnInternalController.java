package com.bluewhale.crm.service.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.service.application.command.ReturnApprovalCallbackCommand;
import com.bluewhale.crm.service.application.service.ReturnRequestAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退换货审批回调控制器（仅内网：whale-workflow）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/internal/return")
@RequiredArgsConstructor
public class ReturnInternalController {

    private final ReturnRequestAppService returnRequestAppService;

    /**
     * 处理审批结果回调（幂等）。
     *
     * @param command 回调命令
     * @return 状态是否发生变化
     */
    @PostMapping("/approval-callback")
    public Result<Boolean> approvalCallback(@Valid @RequestBody ReturnApprovalCallbackCommand command) {
        return Result.ok(returnRequestAppService.handleApprovalCallback(command));
    }
}
