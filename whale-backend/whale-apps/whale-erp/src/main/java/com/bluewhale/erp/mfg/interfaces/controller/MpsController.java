package com.bluewhale.erp.mfg.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.mfg.application.command.MpsCreateCommand;
import com.bluewhale.erp.mfg.application.service.MpsAppService;
import com.bluewhale.erp.mfg.application.view.MpsPlanView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
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
 * 主生产计划接口：查询、建单、下达、取消。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/mfg/mps", version = "1")
@RequiredArgsConstructor
public class MpsController {

    private final MpsAppService mpsAppService;

    /**
     * 主生产计划分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 计划分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:mps:list")
    public Result<PageResult<MpsPlanView>> page(
            @ModelAttribute MfgQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(mpsAppService.page(query));
    }

    /**
     * 主生产计划详情。
     *
     * @param id 计划 ID
     * @return 计划视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:mps:list")
    public Result<MpsPlanView> detail(@PathVariable Long id) {
        return Result.ok(mpsAppService.detail(id));
    }

    /**
     * 新增主生产计划。
     *
     * @param command 创建命令
     * @return 计划视图
     */
    @PostMapping
    @RequirePermission("sys:erp:mps:add")
    @AuditLog(operation = "新增主生产计划", targetType = "erp_mfg_mps", type = OperateType.CREATE)
    public Result<MpsPlanView> create(@Valid @RequestBody MpsCreateCommand command) {
        return Result.ok(mpsAppService.create(command));
    }

    /**
     * 下达主生产计划。
     *
     * @param id 计划 ID
     * @return 计划视图
     */
    @PostMapping("/{id}/release")
    @RequirePermission("sys:erp:mps:release")
    @AuditLog(operation = "下达主生产计划", targetType = "erp_mfg_mps", type = OperateType.UPDATE)
    public Result<MpsPlanView> release(@PathVariable Long id) {
        return Result.ok(mpsAppService.release(id));
    }

    /**
     * 取消主生产计划。
     *
     * @param id 计划 ID
     * @return 计划视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:erp:mps:cancel")
    @AuditLog(operation = "取消主生产计划", targetType = "erp_mfg_mps", type = OperateType.UPDATE)
    public Result<MpsPlanView> cancel(@PathVariable Long id) {
        return Result.ok(mpsAppService.cancel(id));
    }
}
