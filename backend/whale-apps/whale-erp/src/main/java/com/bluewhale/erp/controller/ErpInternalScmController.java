package com.bluewhale.erp.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.scm.application.command.SalesOrderInternalCreateCommand;
import com.bluewhale.erp.scm.application.command.SalesReturnInternalCreateCommand;
import com.bluewhale.erp.scm.application.service.SalesAppService;
import com.bluewhale.erp.scm.application.service.SalesReturnAppService;
import com.bluewhale.erp.scm.application.view.SalesOrderView;
import com.bluewhale.erp.scm.application.view.SalesReturnView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP 销售订单内网契约接口。
 *
 * <p>供 whale-crm 审批通过后按 {@code crmOrderNo} 幂等下发商务订单。网关已将
 * {@code /erp/internal/**} 加入 internal-deny，外部访问 403。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/internal/scm")
@RequiredArgsConstructor
public class ErpInternalScmController {

    private final SalesAppService salesAppService;

    private final SalesReturnAppService salesReturnAppService;

    /**
     * 按 CRM 商务订单号幂等创建销售订单。
     *
     * <p>重复请求（包括并发重复）返回 ERP 已存在的原单，不重复建单。</p>
     *
     * @param command 下发命令
     * @return 销售订单视图
     */
    @PostMapping("/sales-order")
    public Result<SalesOrderView> create(@Valid @RequestBody SalesOrderInternalCreateCommand command) {
        return Result.ok(salesAppService.createFromCrm(command));
    }

    /**
     * 按 CRM 商务订单号查询履约摘要。
     *
     * @param crmOrderNo CRM 商务订单号
     * @return 销售订单视图；不存在时 data 为 null
     */
    @GetMapping("/sales-order/by-crm-order-no")
    public Result<SalesOrderView> byCrmOrderNo(@RequestParam("crmOrderNo") String crmOrderNo) {
        return Result.ok(salesAppService.findByCrmOrderNo(crmOrderNo));
    }

    /**
     * 按 ERP 订单号取消订单（重复取消幂等，已发货拒绝）。
     *
     * @param docNo ERP 订单号
     * @return 销售订单视图
     */
    @PostMapping("/sales-order/{docNo}/cancel")
    public Result<SalesOrderView> cancel(@PathVariable("docNo") String docNo) {
        return Result.ok(salesAppService.cancelByDocNo(docNo));
    }

    /**
     * 按 CRM 退换货申请单号幂等创建并过账销售退货。
     *
     * <p>重复请求返回 ERP 已存在的退货单，不重复过账。</p>
     *
     * @param command 下发命令
     * @return 销售退货视图
     */
    @PostMapping("/sales-return")
    public Result<SalesReturnView> createReturn(
            @Valid @RequestBody SalesReturnInternalCreateCommand command) {
        return Result.ok(salesReturnAppService.createInternal(command));
    }

    /**
     * 按 CRM 退换货申请单号查询销售退货单。
     *
     * @param crmReturnNo CRM 退换货申请单号
     * @return 销售退货视图；不存在时 data 为 null
     */
    @GetMapping("/sales-return/by-crm-return-no")
    public Result<SalesReturnView> byCrmReturnNo(@RequestParam("crmReturnNo") String crmReturnNo) {
        return Result.ok(salesReturnAppService.findByCrmReturnNo(crmReturnNo));
    }
}
