package com.bluewhale.erp.finance.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.finance.application.command.PayableCreateCommand;
import com.bluewhale.erp.finance.application.command.ReceivableCreateCommand;
import com.bluewhale.erp.finance.application.command.SettleCommand;
import com.bluewhale.erp.finance.application.service.ArApAppService;
import com.bluewhale.erp.finance.application.view.AgingView;
import com.bluewhale.erp.finance.application.view.PayableView;
import com.bluewhale.erp.finance.application.view.ReceivableView;
import com.bluewhale.erp.finance.domain.model.ArApQuery;
import com.bluewhale.erp.interfaces.support.PageParams;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 应收应付接口：台账查询、建单、核销、坏账计提与账龄分析。
 *
 * <p>M1 支持手工建单；M2 起由采购收货 / 销售发货自动生成，届时以
 * {@code sourceType + sourceId} 保证幂等。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/finance", version = "1")
@RequiredArgsConstructor
public class ArApController {

    private final ArApAppService arApAppService;

    /**
     * 应收分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 应收分页
     */
    @GetMapping("/receivable/page")
    @RequirePermission("sys:erp:receivable:list")
    public Result<PageResult<ReceivableView>> receivablePage(
            @ModelAttribute ArApQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(arApAppService.receivablePage(query));
    }

    /**
     * 新增应收单。
     *
     * @param command 创建命令
     * @return 应收视图
     */
    @PostMapping("/receivable")
    @RequirePermission("sys:erp:receivable:add")
    @AuditLog(operation = "新增应收单", type = OperateType.CREATE, targetType = "erp_fin_receivable")
    public Result<ReceivableView> createReceivable(@Valid @RequestBody ReceivableCreateCommand command) {
        return Result.ok(arApAppService.createReceivable(command));
    }

    /**
     * 应收核销。
     *
     * @param id 应收单 ID
     * @param command 核销命令
     * @return 应收视图
     */
    @PostMapping("/receivable/{id}/settle")
    @RequirePermission("sys:erp:receivable:settle")
    @AuditLog(operation = "应收核销", type = OperateType.UPDATE, targetType = "erp_fin_receivable", targetId = "#id")
    public Result<ReceivableView> settleReceivable(@PathVariable Long id,
                                                   @Valid @RequestBody SettleCommand command) {
        return Result.ok(arApAppService.settleReceivable(id, command));
    }

    /**
     * 应收坏账计提。
     *
     * @param id 应收单 ID
     * @param command 计提命令
     * @return 应收视图
     */
    @PostMapping("/receivable/{id}/bad-debt")
    @RequirePermission("sys:erp:receivable:bad-debt")
    @AuditLog(operation = "应收坏账计提", type = OperateType.UPDATE, targetType = "erp_fin_receivable",
            targetId = "#id")
    public Result<ReceivableView> provisionBadDebt(@PathVariable Long id,
                                                   @Valid @RequestBody SettleCommand command) {
        return Result.ok(arApAppService.provisionBadDebt(id, command));
    }

    /**
     * 应付分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 应付分页
     */
    @GetMapping("/payable/page")
    @RequirePermission("sys:erp:payable:list")
    public Result<PageResult<PayableView>> payablePage(
            @ModelAttribute ArApQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(arApAppService.payablePage(query));
    }

    /**
     * 新增应付单。
     *
     * @param command 创建命令
     * @return 应付视图
     */
    @PostMapping("/payable")
    @RequirePermission("sys:erp:payable:add")
    @AuditLog(operation = "新增应付单", type = OperateType.CREATE, targetType = "erp_fin_payable")
    public Result<PayableView> createPayable(@Valid @RequestBody PayableCreateCommand command) {
        return Result.ok(arApAppService.createPayable(command));
    }

    /**
     * 应付付款核销。
     *
     * @param id 应付单 ID
     * @param command 付款命令
     * @return 应付视图
     */
    @PostMapping("/payable/{id}/settle")
    @RequirePermission("sys:erp:payable:settle")
    @AuditLog(operation = "应付付款核销", type = OperateType.UPDATE, targetType = "erp_fin_payable", targetId = "#id")
    public Result<PayableView> settlePayable(@PathVariable Long id, @Valid @RequestBody SettleCommand command) {
        return Result.ok(arApAppService.settlePayable(id, command));
    }

    /**
     * 应收账龄分析。
     *
     * @return 按客户汇总的账龄
     */
    @GetMapping("/aging/receivable")
    @RequirePermission("sys:erp:aging:view")
    public Result<List<AgingView>> receivableAging() {
        return Result.ok(arApAppService.receivableAging());
    }

    /**
     * 应付账龄分析。
     *
     * @return 按供应商汇总的账龄
     */
    @GetMapping("/aging/payable")
    @RequirePermission("sys:erp:aging:view")
    public Result<List<AgingView>> payableAging() {
        return Result.ok(arApAppService.payableAging());
    }
}
