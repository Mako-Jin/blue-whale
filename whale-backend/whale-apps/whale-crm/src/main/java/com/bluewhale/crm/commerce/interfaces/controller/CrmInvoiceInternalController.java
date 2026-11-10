package com.bluewhale.crm.commerce.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.commerce.application.command.InvoiceApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.service.InvoiceAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开票审批回调内网接口。
 *
 * <p>由 whale-workflow 回调；网关已将 {@code /crm/internal/**} 加入 internal-deny。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/internal/invoice")
@RequiredArgsConstructor
public class CrmInvoiceInternalController {

    private final InvoiceAppService invoiceAppService;

    /**
     * 处理审批结果回调（幂等）。
     *
     * @param command 回调命令
     * @return 状态是否发生变化
     */
    @PostMapping("/approval-callback")
    public Result<Boolean> approvalCallback(@Valid @RequestBody InvoiceApprovalCallbackCommand command) {
        return Result.ok(invoiceAppService.handleApprovalCallback(command));
    }
}
