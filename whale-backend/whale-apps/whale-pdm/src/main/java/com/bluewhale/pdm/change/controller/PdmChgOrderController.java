package com.bluewhale.pdm.change.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.change.dto.PdmChgApproveRequest;
import com.bluewhale.pdm.change.dto.PdmChgOrderAssessRequest;
import com.bluewhale.pdm.change.dto.PdmChgOrderQueryRequest;
import com.bluewhale.pdm.change.dto.PdmChgOrderSaveRequest;
import com.bluewhale.pdm.change.dto.PdmChgTaskCompleteRequest;
import com.bluewhale.pdm.change.dto.PdmChgTaskSaveRequest;
import com.bluewhale.pdm.change.service.PdmChgOrderService;
import com.bluewhale.pdm.change.vo.PdmChgOrderVO;
import com.bluewhale.pdm.part.support.PdmBomRequestContext;
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
 * PDM 变更单接口（ECO）
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pdm/v{version}/change/order", version = "1")
@RequiredArgsConstructor
public class PdmChgOrderController {

    /** 变更单审计对象类型 */
    private static final String ORDER_TARGET_TYPE = "pdm_chg_order";

    /** 变更任务审计对象类型 */
    private static final String TASK_TARGET_TYPE = "pdm_chg_task";

    private final PdmChgOrderService chgOrderService;

    /**
     * 分页查询变更单
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:change:order:list")
    public Result<PageResult<PdmChgOrderVO>> page(PdmChgOrderQueryRequest request) {
        return Result.ok(chgOrderService.page(request));
    }

    /**
     * 变更单详情
     *
     * @param id 变更单ID
     * @return 变更单详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pdm:change:order:list")
    public Result<PdmChgOrderVO> detail(@PathVariable Long id) {
        return Result.ok(chgOrderService.detail(id));
    }

    /**
     * 从已通过 ECR 新建变更单
     *
     * @param request     保存请求
     * @param httpRequest 当前请求
     * @return 变更单详情
     */
    @PostMapping
    @RequirePermission("sys:pdm:change:order:add")
    @AuditLog(operation = "新建变更单", type = OperateType.CREATE, targetType = ORDER_TARGET_TYPE)
    public Result<PdmChgOrderVO> create(@Valid @RequestBody PdmChgOrderSaveRequest request,
                                        HttpServletRequest httpRequest) {
        return Result.ok(chgOrderService.create(request, PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 修改变更单
     *
     * @param id      变更单ID
     * @param request 保存请求
     * @return 变更单详情
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pdm:change:order:edit")
    @AuditLog(operation = "修改变更单", type = OperateType.UPDATE,
            targetType = ORDER_TARGET_TYPE, targetId = "#id")
    public Result<PdmChgOrderVO> update(@PathVariable Long id,
                                        @Valid @RequestBody PdmChgOrderSaveRequest request) {
        return Result.ok(chgOrderService.update(id, request));
    }

    /**
     * 删除草稿变更单
     *
     * @param id 变更单ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:change:order:delete")
    @AuditLog(operation = "删除变更单", type = OperateType.DELETE,
            targetType = ORDER_TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        chgOrderService.delete(id);
        return Result.ok();
    }

    /**
     * 影响评估
     *
     * @param id      变更单ID
     * @param request 评估请求
     * @return 变更单详情
     */
    @PostMapping("/{id}/assess")
    @RequirePermission("sys:pdm:change:order:edit")
    @AuditLog(operation = "变更单影响评估", type = OperateType.UPDATE,
            targetType = ORDER_TARGET_TYPE, targetId = "#id")
    public Result<PdmChgOrderVO> assess(@PathVariable Long id,
                                        @Valid @RequestBody PdmChgOrderAssessRequest request) {
        return Result.ok(chgOrderService.assess(id, request));
    }

    /**
     * 新增变更任务
     *
     * @param id      变更单ID
     * @param request 任务请求
     * @return 变更单详情
     */
    @PostMapping("/{id}/tasks")
    @RequirePermission("sys:pdm:change:order:edit")
    @AuditLog(operation = "新增变更任务", type = OperateType.CREATE, targetType = TASK_TARGET_TYPE)
    public Result<PdmChgOrderVO> addTask(@PathVariable Long id,
                                         @Valid @RequestBody PdmChgTaskSaveRequest request) {
        return Result.ok(chgOrderService.addTask(id, request));
    }

    /**
     * 修改变更任务
     *
     * @param id      变更单ID
     * @param taskId  任务ID
     * @param request 任务请求
     * @return 变更单详情
     */
    @PutMapping("/{id}/tasks/{taskId}")
    @RequirePermission("sys:pdm:change:order:edit")
    @AuditLog(operation = "修改变更任务", type = OperateType.UPDATE,
            targetType = TASK_TARGET_TYPE, targetId = "#taskId")
    public Result<PdmChgOrderVO> updateTask(@PathVariable Long id, @PathVariable Long taskId,
                                            @Valid @RequestBody PdmChgTaskSaveRequest request) {
        return Result.ok(chgOrderService.updateTask(id, taskId, request));
    }

    /**
     * 删除变更任务
     *
     * @param id     变更单ID
     * @param taskId 任务ID
     * @return 变更单详情
     */
    @DeleteMapping("/{id}/tasks/{taskId}")
    @RequirePermission("sys:pdm:change:order:edit")
    @AuditLog(operation = "删除变更任务", type = OperateType.DELETE,
            targetType = TASK_TARGET_TYPE, targetId = "#taskId")
    public Result<PdmChgOrderVO> deleteTask(@PathVariable Long id, @PathVariable Long taskId) {
        return Result.ok(chgOrderService.deleteTask(id, taskId));
    }

    /**
     * 提交审批
     *
     * @param id 变更单ID
     * @return 变更单详情
     */
    @PostMapping("/{id}/submit")
    @RequirePermission("sys:pdm:change:order:submit")
    @AuditLog(operation = "提交变更单", type = OperateType.UPDATE,
            targetType = ORDER_TARGET_TYPE, targetId = "#id")
    public Result<PdmChgOrderVO> submit(@PathVariable Long id) {
        return Result.ok(chgOrderService.submit(id));
    }

    /**
     * 审批通过
     *
     * @param id          变更单ID
     * @param request     审批请求
     * @param httpRequest 当前请求
     * @return 变更单详情
     */
    @PostMapping("/{id}/approve")
    @RequirePermission("sys:pdm:change:order:approve")
    @AuditLog(operation = "审批通过变更单", type = OperateType.UPDATE,
            targetType = ORDER_TARGET_TYPE, targetId = "#id")
    public Result<PdmChgOrderVO> approve(@PathVariable Long id,
                                         @Valid @RequestBody(required = false) PdmChgApproveRequest request,
                                         HttpServletRequest httpRequest) {
        return Result.ok(chgOrderService.approve(id, commentOf(request),
                PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 审批驳回
     *
     * @param id          变更单ID
     * @param request     审批请求
     * @param httpRequest 当前请求
     * @return 变更单详情
     */
    @PostMapping("/{id}/reject")
    @RequirePermission("sys:pdm:change:order:approve")
    @AuditLog(operation = "驳回变更单", type = OperateType.UPDATE,
            targetType = ORDER_TARGET_TYPE, targetId = "#id")
    public Result<PdmChgOrderVO> reject(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) PdmChgApproveRequest request,
                                        HttpServletRequest httpRequest) {
        return Result.ok(chgOrderService.reject(id, commentOf(request),
                PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 执行变更单
     *
     * @param id 变更单ID
     * @return 变更单详情
     */
    @PostMapping("/{id}/execute")
    @RequirePermission("sys:pdm:change:order:execute")
    @AuditLog(operation = "执行变更单", type = OperateType.UPDATE,
            targetType = ORDER_TARGET_TYPE, targetId = "#id")
    public Result<PdmChgOrderVO> execute(@PathVariable Long id) {
        return Result.ok(chgOrderService.execute(id));
    }

    /**
     * 完成任务并登记结果版本号
     *
     * @param id      变更单ID
     * @param taskId  任务ID
     * @param request 完成请求
     * @return 变更单详情
     */
    @PostMapping("/{id}/tasks/{taskId}/complete")
    @RequirePermission("sys:pdm:change:order:execute")
    @AuditLog(operation = "完成变更任务", type = OperateType.UPDATE,
            targetType = TASK_TARGET_TYPE, targetId = "#taskId")
    public Result<PdmChgOrderVO> completeTask(@PathVariable Long id, @PathVariable Long taskId,
                                              @Valid @RequestBody PdmChgTaskCompleteRequest request) {
        return Result.ok(chgOrderService.completeTask(id, taskId, request.getResultVersionNo()));
    }

    /**
     * 完成变更单
     *
     * @param id 变更单ID
     * @return 变更单详情
     */
    @PostMapping("/{id}/complete")
    @RequirePermission("sys:pdm:change:order:execute")
    @AuditLog(operation = "完成变更单", type = OperateType.UPDATE,
            targetType = ORDER_TARGET_TYPE, targetId = "#id")
    public Result<PdmChgOrderVO> complete(@PathVariable Long id) {
        return Result.ok(chgOrderService.complete(id));
    }

    /**
     * 取消变更单
     *
     * @param id 变更单ID
     * @return 变更单详情
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:pdm:change:order:edit")
    @AuditLog(operation = "取消变更单", type = OperateType.UPDATE,
            targetType = ORDER_TARGET_TYPE, targetId = "#id")
    public Result<PdmChgOrderVO> cancel(@PathVariable Long id) {
        return Result.ok(chgOrderService.cancel(id));
    }

    /**
     * 关闭变更单
     *
     * @param id 变更单ID
     * @return 变更单详情
     */
    @PostMapping("/{id}/close")
    @RequirePermission("sys:pdm:change:order:edit")
    @AuditLog(operation = "关闭变更单", type = OperateType.UPDATE,
            targetType = ORDER_TARGET_TYPE, targetId = "#id")
    public Result<PdmChgOrderVO> close(@PathVariable Long id) {
        return Result.ok(chgOrderService.close(id));
    }

    /**
     * 提取审批意见。
     *
     * @param request 审批请求
     * @return 审批意见
     */
    private static String commentOf(PdmChgApproveRequest request) {
        return request == null ? null : request.getComment();
    }
}
