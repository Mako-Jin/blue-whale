package com.bluewhale.crm.commerce.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.commerce.application.command.QuoteApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.service.QuoteAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报价内网接口（whale-workflow 回调）。
 *
 * <p>仅内网可达；网关对 {@code /crm/internal/**} 外部请求统一 403，故不登记权限码。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/internal/quote")
@RequiredArgsConstructor
public class QuoteInternalController {

    private final QuoteAppService quoteAppService;

    /**
     * 审批结果回调（幂等）。
     *
     * @param command 回调命令
     * @return 本次是否推进了状态
     */
    @PostMapping("/approval-callback")
    public Result<Boolean> approvalCallback(@Valid @RequestBody QuoteApprovalCallbackCommand command) {
        return Result.ok(quoteAppService.handleApprovalCallback(command));
    }
}
