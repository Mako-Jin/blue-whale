package com.bluewhale.erp.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.finance.application.service.ArApAppService;
import com.bluewhale.erp.finance.application.view.CustomerReceivableView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP 财务内网只读接口：按客户查询未结应收与账龄。
 *
 * <p>仅供内网服务调用，网关对 {@code /erp/internal/**} 返回 403；本控制器不写库。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/internal/finance")
@RequiredArgsConstructor
public class ErpInternalFinanceController {

    private final ArApAppService arApAppService;

    /**
     * 按客户编码查询未结应收汇总与明细。
     *
     * @param customerCode 客户编码
     * @return 客户应收汇总
     */
    @GetMapping("/receivable/by-customer")
    public Result<CustomerReceivableView> receivableByCustomer(
            @RequestParam("customerCode") String customerCode) {
        return Result.ok(arApAppService.customerReceivable(customerCode));
    }
}
