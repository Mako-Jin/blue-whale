package com.bluewhale.erp.masterdata.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.ErpUserContext;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.masterdata.application.command.AccountSaveCommand;
import com.bluewhale.erp.masterdata.application.command.CustomerSaveCommand;
import com.bluewhale.erp.masterdata.application.command.WarehouseSaveCommand;
import com.bluewhale.erp.masterdata.application.service.MasterDataAppService;
import com.bluewhale.erp.masterdata.application.view.AccountView;
import com.bluewhale.erp.masterdata.application.view.CustomerView;
import com.bluewhale.erp.masterdata.application.view.PeriodView;
import com.bluewhale.erp.masterdata.application.view.ReferenceDataView;
import com.bluewhale.erp.masterdata.application.view.WarehouseView;
import com.bluewhale.erp.masterdata.domain.model.MasterDataQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP 主数据接口：科目、会计期间、客户、仓库与参考数据。
 *
 * <p>路径前缀 {@code /erp/master-data}，网关按 {@code Path=/erp/**} 原样转发。
 * 账套主数据（科目/期间）与业务主数据（客户/仓库）分开授权，关账与反结账为显式动作端点。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/master-data")
@RequiredArgsConstructor
public class MasterDataController {

    private final MasterDataAppService masterDataAppService;

    /**
     * 科目分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 科目分页
     */
    @GetMapping("/account/page")
    @RequirePermission("sys:erp:account:list")
    public Result<PageResult<AccountView>> accountPage(
            @ModelAttribute MasterDataQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(masterDataAppService.accountPage(query));
    }

    /**
     * 新增科目。
     *
     * @param command 新增命令
     * @return 科目视图
     */
    @PostMapping("/account")
    @RequirePermission("sys:erp:account:add")
    @AuditLog(operation = "新增会计科目", type = OperateType.CREATE, targetType = "erp_account")
    public Result<AccountView> createAccount(@Valid @RequestBody AccountSaveCommand command) {
        return Result.ok(masterDataAppService.createAccount(command));
    }

    /**
     * 编辑科目。
     *
     * @param id 科目 ID
     * @param command 编辑命令
     * @return 科目视图
     */
    @PutMapping("/account/{id}")
    @RequirePermission("sys:erp:account:edit")
    @AuditLog(operation = "编辑会计科目", type = OperateType.UPDATE, targetType = "erp_account", targetId = "#id")
    public Result<AccountView> updateAccount(@PathVariable Long id,
                                             @Valid @RequestBody AccountSaveCommand command) {
        return Result.ok(masterDataAppService.updateAccount(id, command));
    }

    /**
     * 会计期间分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 期间分页
     */
    @GetMapping("/period/page")
    @RequirePermission("sys:erp:period:list")
    public Result<PageResult<PeriodView>> periodPage(
            @ModelAttribute MasterDataQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(masterDataAppService.periodPage(query));
    }

    /**
     * 关账。
     *
     * @param id 期间 ID
     * @param request 当前请求（取操作人）
     * @return 期间视图
     */
    @PostMapping("/period/{id}/close")
    @RequirePermission("sys:erp:period:close")
    @AuditLog(operation = "会计期间关账", type = OperateType.UPDATE, targetType = "erp_accounting_period",
            targetId = "#id")
    public Result<PeriodView> closePeriod(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(masterDataAppService.closePeriod(id, ErpUserContext.userId(request)));
    }

    /**
     * 反结账（受控操作，单独权限并留审计）。
     *
     * @param id 期间 ID
     * @return 期间视图
     */
    @PostMapping("/period/{id}/reopen")
    @RequirePermission("sys:erp:period:reopen")
    @AuditLog(operation = "会计期间反结账", type = OperateType.UPDATE, targetType = "erp_accounting_period",
            targetId = "#id")
    public Result<PeriodView> reopenPeriod(@PathVariable Long id) {
        return Result.ok(masterDataAppService.reopenPeriod(id));
    }

    /**
     * 客户分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 客户分页
     */
    @GetMapping("/customer/page")
    @RequirePermission("sys:erp:customer:list")
    public Result<PageResult<CustomerView>> customerPage(
            @ModelAttribute MasterDataQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(masterDataAppService.customerPage(query));
    }

    /**
     * 新增客户。
     *
     * @param command 新增命令
     * @return 客户视图
     */
    @PostMapping("/customer")
    @RequirePermission("sys:erp:customer:add")
    @AuditLog(operation = "新增客户主数据", type = OperateType.CREATE, targetType = "erp_customer")
    public Result<CustomerView> createCustomer(@Valid @RequestBody CustomerSaveCommand command) {
        return Result.ok(masterDataAppService.createCustomer(command));
    }

    /**
     * 编辑客户。
     *
     * @param id 客户 ID
     * @param command 编辑命令
     * @return 客户视图
     */
    @PutMapping("/customer/{id}")
    @RequirePermission("sys:erp:customer:edit")
    @AuditLog(operation = "编辑客户主数据", type = OperateType.UPDATE, targetType = "erp_customer", targetId = "#id")
    public Result<CustomerView> updateCustomer(@PathVariable Long id,
                                               @Valid @RequestBody CustomerSaveCommand command) {
        return Result.ok(masterDataAppService.updateCustomer(id, command));
    }

    /**
     * 仓库分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 仓库分页
     */
    @GetMapping("/warehouse/page")
    @RequirePermission("sys:erp:warehouse:list")
    public Result<PageResult<WarehouseView>> warehousePage(
            @ModelAttribute MasterDataQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(masterDataAppService.warehousePage(query));
    }

    /**
     * 新增仓库。
     *
     * @param command 新增命令
     * @return 仓库视图
     */
    @PostMapping("/warehouse")
    @RequirePermission("sys:erp:warehouse:add")
    @AuditLog(operation = "新增仓库", type = OperateType.CREATE, targetType = "erp_warehouse")
    public Result<WarehouseView> createWarehouse(@Valid @RequestBody WarehouseSaveCommand command) {
        return Result.ok(masterDataAppService.createWarehouse(command));
    }

    /**
     * 编辑仓库。
     *
     * @param id 仓库 ID
     * @param command 编辑命令
     * @return 仓库视图
     */
    @PutMapping("/warehouse/{id}")
    @RequirePermission("sys:erp:warehouse:edit")
    @AuditLog(operation = "编辑仓库", type = OperateType.UPDATE, targetType = "erp_warehouse", targetId = "#id")
    public Result<WarehouseView> updateWarehouse(@PathVariable Long id,
                                                 @Valid @RequestBody WarehouseSaveCommand command) {
        return Result.ok(masterDataAppService.updateWarehouse(id, command));
    }

    /**
     * 参考数据（计量单位 / 币种 / 税码）。
     *
     * @return 参考数据
     */
    @GetMapping("/reference")
    @RequirePermission("sys:erp:reference:list")
    public Result<ReferenceDataView> reference() {
        return Result.ok(masterDataAppService.referenceData());
    }
}
