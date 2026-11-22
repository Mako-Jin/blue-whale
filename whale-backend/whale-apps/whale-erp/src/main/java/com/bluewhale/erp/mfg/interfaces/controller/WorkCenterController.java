package com.bluewhale.erp.mfg.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.mfg.application.command.WorkCenterSaveCommand;
import com.bluewhale.erp.mfg.application.service.WorkCenterAppService;
import com.bluewhale.erp.mfg.application.view.WorkCenterView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
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

import java.util.List;

/**
 * 工作中心接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/mfg/work-centers", version = "1")
@RequiredArgsConstructor
public class WorkCenterController {

    private final WorkCenterAppService workCenterAppService;

    /**
     * 工作中心分页。
     *
     * @param query    查询条件
     * @param current  后端风格页码
     * @param pageNum  前端风格页码
     * @param size     后端风格条数
     * @param pageSize 前端风格条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:mfg:workcenter:list")
    public Result<PageResult<WorkCenterView>> page(
            @ModelAttribute MfgQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(workCenterAppService.page(query));
    }

    /**
     * 全部工作中心（下拉用）。
     *
     * @return 工作中心列表
     */
    @GetMapping("/all")
    @RequirePermission("sys:erp:mfg:workcenter:list")
    public Result<List<WorkCenterView>> listAll() {
        return Result.ok(workCenterAppService.listAll());
    }

    /**
     * 新增工作中心。
     *
     * @param command 保存命令
     * @return 工作中心视图
     */
    @PostMapping
    @RequirePermission("sys:erp:mfg:workcenter:add")
    @AuditLog(operation = "新增工作中心", targetType = "erp_mfg_work_center", type = OperateType.CREATE)
    public Result<WorkCenterView> create(@Valid @RequestBody WorkCenterSaveCommand command) {
        return Result.ok(workCenterAppService.create(command));
    }

    /**
     * 编辑工作中心。
     *
     * @param id      主键
     * @param command 保存命令
     * @return 工作中心视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:erp:mfg:workcenter:edit")
    @AuditLog(operation = "编辑工作中心", targetType = "erp_mfg_work_center", type = OperateType.UPDATE)
    public Result<WorkCenterView> update(@PathVariable Long id,
                                         @Valid @RequestBody WorkCenterSaveCommand command) {
        return Result.ok(workCenterAppService.update(id, command));
    }

    /**
     * 删除工作中心。
     *
     * @param id 主键
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:erp:mfg:workcenter:delete")
    @AuditLog(operation = "删除工作中心", targetType = "erp_mfg_work_center", type = OperateType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        workCenterAppService.delete(id);
        return Result.ok();
    }
}
