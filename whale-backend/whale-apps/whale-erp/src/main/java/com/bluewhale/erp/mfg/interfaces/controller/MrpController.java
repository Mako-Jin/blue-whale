package com.bluewhale.erp.mfg.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.mfg.application.command.MrpRunCommand;
import com.bluewhale.erp.mfg.application.service.MrpAppService;
import com.bluewhale.erp.mfg.application.view.MrpPlannedOrderView;
import com.bluewhale.erp.mfg.application.view.MrpRunView;
import com.bluewhale.erp.mfg.domain.model.MrpQuery;
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
 * MRP 接口：执行运算、重跑、运行记录与计划订单查询。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/mfg/mrp", version = "1")
@RequiredArgsConstructor
public class MrpController {

    private final MrpAppService mrpAppService;

    /**
     * 执行 MRP 运算（同步）。
     *
     * @param command 运算命令
     * @return 运行记录详情
     */
    @PostMapping("/runs")
    @RequirePermission("sys:erp:mrp:run")
    @AuditLog(operation = "执行MRP运算", targetType = "erp_mfg_mrp_run", type = OperateType.CREATE)
    public Result<MrpRunView> run(@Valid @RequestBody MrpRunCommand command) {
        return Result.ok(mrpAppService.run(command));
    }

    /**
     * 按原输入重跑。
     *
     * @param id 原运行记录 ID
     * @return 新运行记录详情
     */
    @PostMapping("/runs/{id}/rerun")
    @RequirePermission("sys:erp:mrp:run")
    @AuditLog(operation = "重跑MRP运算", targetType = "erp_mfg_mrp_run", type = OperateType.UPDATE)
    public Result<MrpRunView> rerun(@PathVariable Long id) {
        return Result.ok(mrpAppService.rerun(id));
    }

    /**
     * 运行记录分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 运行记录分页
     */
    @GetMapping("/runs/page")
    @RequirePermission("sys:erp:mrp:run:list")
    public Result<PageResult<MrpRunView>> pageRuns(
            @ModelAttribute MrpQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(mrpAppService.pageRuns(query));
    }

    /**
     * 运行记录详情。
     *
     * @param id 运行记录 ID
     * @return 运行记录详情
     */
    @GetMapping("/runs/{id}")
    @RequirePermission("sys:erp:mrp:run:list")
    public Result<MrpRunView> detailRun(@PathVariable Long id) {
        return Result.ok(mrpAppService.detailRun(id));
    }

    /**
     * 计划订单分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 计划订单分页
     */
    @GetMapping("/orders/page")
    @RequirePermission("sys:erp:mrp:order:list")
    public Result<PageResult<MrpPlannedOrderView>> pageOrders(
            @ModelAttribute MrpQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(mrpAppService.pageOrders(query));
    }

    /**
     * 计划订单详情（含需求追溯）。
     *
     * @param id 计划订单 ID
     * @return 计划订单详情
     */
    @GetMapping("/orders/{id}")
    @RequirePermission("sys:erp:mrp:order:list")
    public Result<MrpPlannedOrderView> detailOrder(@PathVariable Long id) {
        return Result.ok(mrpAppService.detailOrder(id));
    }
}
