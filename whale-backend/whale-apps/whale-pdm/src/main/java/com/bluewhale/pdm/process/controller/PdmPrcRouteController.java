package com.bluewhale.pdm.process.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.process.dto.PdmPrcRouteQueryRequest;
import com.bluewhale.pdm.process.dto.PdmPrcRouteSaveRequest;
import com.bluewhale.pdm.process.dto.PdmPrcSopSaveRequest;
import com.bluewhale.pdm.process.dto.PdmPrcStepSaveRequest;
import com.bluewhale.pdm.process.dto.PdmPrcToolingSaveRequest;
import com.bluewhale.pdm.process.service.PdmPrcRouteService;
import com.bluewhale.pdm.process.support.PdmPrcRequestContext;
import com.bluewhale.pdm.process.vo.PdmPrcRouteVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDM 工艺路线接口
 *
 * <p>路线发布后不可编辑；工序、工装、SOP 均以动作端点维护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/process/route")
@RequiredArgsConstructor
public class PdmPrcRouteController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "pdm_prc_route";

    /** 审计模块 */
    private static final String MODULE = "PDM 工艺管理";

    private final PdmPrcRouteService routeService;

    /**
     * 分页查询工艺路线
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:process:list")
    public Result<PageResult<PdmPrcRouteVO>> page(PdmPrcRouteQueryRequest request) {
        return Result.ok(routeService.page(request));
    }

    /**
     * 查询工艺路线详情
     *
     * @param id 路线ID
     * @return 路线视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pdm:process:list")
    public Result<PdmPrcRouteVO> detail(@PathVariable Long id) {
        return Result.ok(routeService.detail(id));
    }

    /**
     * 新建工艺路线版本
     *
     * @param request 新建请求
     * @param servletRequest 当前请求
     * @return 路线视图
     */
    @PostMapping
    @RequirePermission("sys:pdm:process:add")
    @AuditLog(module = MODULE, type = OperateType.CREATE, operation = "新建工艺路线", targetType = TARGET_TYPE)
    public Result<PdmPrcRouteVO> create(@Valid @RequestBody PdmPrcRouteSaveRequest request,
                                        HttpServletRequest servletRequest) {
        return Result.ok(routeService.create(request,
                PdmPrcRequestContext.requireUserId(servletRequest)));
    }

    /**
     * 更新草稿工艺路线
     *
     * @param id      路线ID
     * @param request 更新请求
     * @return 路线视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pdm:process:edit")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "更新工艺路线",
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmPrcRouteVO> update(@PathVariable Long id,
                                        @Valid @RequestBody PdmPrcRouteSaveRequest request) {
        return Result.ok(routeService.update(id, request));
    }

    /**
     * 删除草稿工艺路线
     *
     * @param id 路线ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:process:delete")
    @AuditLog(module = MODULE, type = OperateType.DELETE, operation = "删除工艺路线",
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        routeService.delete(id);
        return Result.ok();
    }

    /**
     * 新增工序
     *
     * @param id      路线ID
     * @param request 工序请求
     * @return 路线视图
     */
    @PostMapping("/{id}/steps")
    @RequirePermission("sys:pdm:process:edit")
    @AuditLog(module = MODULE, type = OperateType.CREATE, operation = "新增工艺工序",
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmPrcRouteVO> addStep(@PathVariable Long id,
                                         @Valid @RequestBody PdmPrcStepSaveRequest request) {
        return Result.ok(routeService.addStep(id, request));
    }

    /**
     * 更新工序
     *
     * @param id      路线ID
     * @param stepId  工序ID
     * @param request 工序请求
     * @return 路线视图
     */
    @PutMapping("/{id}/steps/{stepId}")
    @RequirePermission("sys:pdm:process:edit")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "更新工艺工序",
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmPrcRouteVO> updateStep(@PathVariable Long id, @PathVariable Long stepId,
                                            @Valid @RequestBody PdmPrcStepSaveRequest request) {
        return Result.ok(routeService.updateStep(id, stepId, request));
    }

    /**
     * 删除工序
     *
     * @param id     路线ID
     * @param stepId 工序ID
     * @return 路线视图
     */
    @DeleteMapping("/{id}/steps/{stepId}")
    @RequirePermission("sys:pdm:process:edit")
    @AuditLog(module = MODULE, type = OperateType.DELETE, operation = "删除工艺工序",
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmPrcRouteVO> deleteStep(@PathVariable Long id, @PathVariable Long stepId) {
        return Result.ok(routeService.deleteStep(id, stepId));
    }

    /**
     * 新增工装
     *
     * @param id      路线ID
     * @param request 工装请求
     * @return 路线视图
     */
    @PostMapping("/{id}/toolings")
    @RequirePermission("sys:pdm:process:edit")
    @AuditLog(module = MODULE, type = OperateType.CREATE, operation = "新增工艺工装",
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmPrcRouteVO> addTooling(@PathVariable Long id,
                                            @Valid @RequestBody PdmPrcToolingSaveRequest request) {
        return Result.ok(routeService.addTooling(id, request));
    }

    /**
     * 删除工装
     *
     * @param id        路线ID
     * @param toolingId 工装ID
     * @return 路线视图
     */
    @DeleteMapping("/{id}/toolings/{toolingId}")
    @RequirePermission("sys:pdm:process:edit")
    @AuditLog(module = MODULE, type = OperateType.DELETE, operation = "删除工艺工装",
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmPrcRouteVO> deleteTooling(@PathVariable Long id, @PathVariable Long toolingId) {
        return Result.ok(routeService.deleteTooling(id, toolingId));
    }

    /**
     * 保存工序 SOP
     *
     * @param id      路线ID
     * @param request SOP 请求
     * @return 路线视图
     */
    @PostMapping("/{id}/sop")
    @RequirePermission("sys:pdm:process:edit")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "维护工序 SOP",
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmPrcRouteVO> saveSop(@PathVariable Long id,
                                         @Valid @RequestBody PdmPrcSopSaveRequest request) {
        return Result.ok(routeService.saveSop(id, request));
    }

    /**
     * 删除工序 SOP
     *
     * @param id    路线ID
     * @param sopId SOP 记录ID
     * @return 路线视图
     */
    @DeleteMapping("/{id}/sop/{sopId}")
    @RequirePermission("sys:pdm:process:edit")
    @AuditLog(module = MODULE, type = OperateType.DELETE, operation = "删除工序 SOP",
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmPrcRouteVO> deleteSop(@PathVariable Long id, @PathVariable Long sopId) {
        return Result.ok(routeService.deleteSop(id, sopId));
    }

    /**
     * 发布工艺路线并下发 MES
     *
     * @param id 路线ID
     * @param servletRequest 当前请求
     * @return 路线视图
     */
    @PostMapping("/{id}/release")
    @RequirePermission("sys:pdm:process:release")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "发布工艺路线",
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmPrcRouteVO> release(@PathVariable Long id, HttpServletRequest servletRequest) {
        return Result.ok(routeService.release(id,
                PdmPrcRequestContext.requireUserId(servletRequest)));
    }

    /**
     * 重试 MES 下发
     *
     * @param id 路线ID
     * @return 路线视图
     */
    @PostMapping("/{id}/publish/retry")
    @RequirePermission("sys:pdm:process:publish")
    @AuditLog(module = MODULE, type = OperateType.OTHER, operation = "重试下发工艺路线",
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmPrcRouteVO> retryPublish(@PathVariable Long id) {
        return Result.ok(routeService.retryPublish(id));
    }

    /**
     * 撤回工艺路线
     *
     * @param id 路线ID
     * @return 路线视图
     */
    @PostMapping("/{id}/withdraw")
    @RequirePermission("sys:pdm:process:withdraw")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "撤回工艺路线",
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmPrcRouteVO> withdraw(@PathVariable Long id) {
        return Result.ok(routeService.withdraw(id));
    }
}
