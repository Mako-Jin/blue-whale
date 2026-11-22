package com.bluewhale.erp.scm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.scm.application.command.SafetyStockCreateCommand;
import com.bluewhale.erp.scm.application.command.SafetyStockUpdateCommand;
import com.bluewhale.erp.scm.application.service.SafetyStockAppService;
import com.bluewhale.erp.scm.application.view.IdleStockView;
import com.bluewhale.erp.scm.application.view.SafetyStockView;
import com.bluewhale.erp.scm.application.view.ShortageView;
import com.bluewhale.erp.scm.domain.model.SafetyStockQuery;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 安全库存接口：配置维护、缺料预警与呆滞分析。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/scm/safety-stock", version = "1")
@RequiredArgsConstructor
public class SafetyStockController {

    private final SafetyStockAppService safetyStockAppService;

    /**
     * 安全库存分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:safety-stock:list")
    public Result<PageResult<SafetyStockView>> page(
            @ModelAttribute SafetyStockQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(safetyStockAppService.page(query));
    }

    /**
     * 缺料预警。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 分页结果
     */
    @GetMapping("/shortage")
    @RequirePermission("sys:erp:safety-stock:analysis")
    public Result<PageResult<ShortageView>> shortage(
            @ModelAttribute SafetyStockQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(safetyStockAppService.shortage(query));
    }

    /**
     * 呆滞分析。
     *
     * @param query 查询条件
     * @param idleDays 呆滞天数阈值（默认 90）
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 分页结果
     */
    @GetMapping("/idle")
    @RequirePermission("sys:erp:safety-stock:analysis")
    public Result<PageResult<IdleStockView>> idle(
            @ModelAttribute ScmQuery query,
            @RequestParam(value = "idleDays", required = false) Integer idleDays,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(safetyStockAppService.idle(query, idleDays));
    }

    /**
     * 安全库存详情。
     *
     * @param id 主键
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:safety-stock:detail")
    public Result<SafetyStockView> detail(@PathVariable Long id) {
        return Result.ok(safetyStockAppService.detail(id));
    }

    /**
     * 新增安全库存配置。
     *
     * @param command 创建命令
     * @return 视图
     */
    @PostMapping
    @RequirePermission("sys:erp:safety-stock:create")
    @AuditLog(operation = "新增安全库存", targetType = "erp_scm_safety_stock", type = OperateType.CREATE)
    public Result<SafetyStockView> create(@Valid @RequestBody SafetyStockCreateCommand command) {
        return Result.ok(safetyStockAppService.create(command));
    }

    /**
     * 更新安全库存配置。
     *
     * @param id 主键
     * @param command 更新命令
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:erp:safety-stock:update")
    @AuditLog(operation = "更新安全库存", targetType = "erp_scm_safety_stock", type = OperateType.UPDATE)
    public Result<SafetyStockView> update(@PathVariable Long id,
                                          @Valid @RequestBody SafetyStockUpdateCommand command) {
        return Result.ok(safetyStockAppService.update(id, command));
    }

    /**
     * 删除安全库存配置（逻辑删除）。
     *
     * @param id 主键
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:erp:safety-stock:delete")
    @AuditLog(operation = "删除安全库存", targetType = "erp_scm_safety_stock", type = OperateType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        safetyStockAppService.delete(id);
        return Result.ok();
    }
}
