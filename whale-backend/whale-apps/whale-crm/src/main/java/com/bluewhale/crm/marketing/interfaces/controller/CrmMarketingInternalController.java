package com.bluewhale.crm.marketing.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.marketing.application.command.DeliveryReceiptCommand;
import com.bluewhale.crm.marketing.application.service.MarketingSendAppService;
import com.bluewhale.crm.marketing.application.view.DeliveryReceiptView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 营销触达内网控制器（仅内网：whale-message 回执回调）。
 *
 * <p>网关 {@code internal-deny} 对 {@code /crm/internal/**} 统一 403，外部不可达；
 * 回执推送不带用户身份，故不加权限码注解。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/internal/marketing")
@RequiredArgsConstructor
public class CrmMarketingInternalController {

    private final MarketingSendAppService marketingSendAppService;

    /**
     * 承接渠道送达回执（幂等）。
     *
     * @param command 回执命令
     * @return 处理结果；已是最新状态时 {@code applied=false}
     */
    @PostMapping("/delivery-callback")
    public Result<DeliveryReceiptView> deliveryCallback(@Valid @RequestBody DeliveryReceiptCommand command) {
        return Result.ok(marketingSendAppService.applyDeliveryReceipt(command));
    }
}
