package com.bluewhale.erp.finance.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.finance.application.command.VoucherCreateCommand;
import com.bluewhale.erp.finance.application.service.VoucherAppService;
import com.bluewhale.erp.finance.application.view.VoucherView;
import com.bluewhale.erp.finance.domain.model.VoucherQuery;
import com.bluewhale.erp.interfaces.support.ErpUserContext;
import com.bluewhale.erp.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
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

/**
 * 会计凭证接口：查询、录入、记账与红冲。
 *
 * <p>记账与红冲是显式动作端点，不允许通过通用 PUT 直接改状态；
 * 已记账凭证不可修改，只能红冲生成反向凭证。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/finance/voucher", version = "1")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherAppService voucherAppService;

    /**
     * 凭证分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 凭证分页（不含分录）
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:voucher:list")
    public Result<PageResult<VoucherView>> page(
            @ModelAttribute VoucherQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(voucherAppService.page(query));
    }

    /**
     * 凭证详情（含分录）。
     *
     * @param id 凭证 ID
     * @return 凭证视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:voucher:list")
    public Result<VoucherView> detail(@PathVariable Long id) {
        return Result.ok(voucherAppService.detail(id));
    }

    /**
     * 创建草稿凭证。
     *
     * @param command 创建命令
     * @return 凭证视图
     */
    @PostMapping
    @RequirePermission("sys:erp:voucher:add")
    @AuditLog(operation = "录入会计凭证", type = OperateType.CREATE, targetType = "erp_fin_voucher")
    public Result<VoucherView> create(@Valid @RequestBody VoucherCreateCommand command) {
        return Result.ok(voucherAppService.create(command));
    }

    /**
     * 记账。
     *
     * @param id 凭证 ID
     * @param request 当前请求（取记账人）
     * @return 凭证视图
     */
    @PostMapping("/{id}/post")
    @RequirePermission("sys:erp:voucher:post")
    @AuditLog(operation = "凭证记账", type = OperateType.UPDATE, targetType = "erp_fin_voucher", targetId = "#id")
    public Result<VoucherView> post(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(voucherAppService.post(id, ErpUserContext.userId(request)));
    }

    /**
     * 红冲（生成反向凭证并标记原凭证）。
     *
     * @param id 原凭证 ID
     * @param request 当前请求（取操作人）
     * @return 红冲凭证视图
     */
    @PostMapping("/{id}/reverse")
    @RequirePermission("sys:erp:voucher:reverse")
    @AuditLog(operation = "凭证红冲", type = OperateType.UPDATE, targetType = "erp_fin_voucher", targetId = "#id")
    public Result<VoucherView> reverse(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(voucherAppService.reverse(id, ErpUserContext.userId(request)));
    }
}
