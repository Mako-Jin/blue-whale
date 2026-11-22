package com.bluewhale.pdm.change.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.change.dto.PdmChgApproveRequest;
import com.bluewhale.pdm.change.dto.PdmChgRequestQueryRequest;
import com.bluewhale.pdm.change.dto.PdmChgRequestSaveRequest;
import com.bluewhale.pdm.change.service.PdmChgRequestService;
import com.bluewhale.pdm.change.vo.PdmChgRequestVO;
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
 * PDM 变更申请接口（ECR）
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pdm/v{version}/change/request", version = "1")
@RequiredArgsConstructor
public class PdmChgRequestController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "pdm_chg_request";

    private final PdmChgRequestService chgRequestService;

    /**
     * 分页查询变更申请
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:change:list")
    public Result<PageResult<PdmChgRequestVO>> page(PdmChgRequestQueryRequest request) {
        return Result.ok(chgRequestService.page(request));
    }

    /**
     * 变更申请详情
     *
     * @param id 申请ID
     * @return 申请详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pdm:change:list")
    public Result<PdmChgRequestVO> detail(@PathVariable Long id) {
        return Result.ok(chgRequestService.detail(id));
    }

    /**
     * 新建变更申请
     *
     * @param request 保存请求
     * @param httpRequest 当前请求
     * @return 申请详情
     */
    @PostMapping
    @RequirePermission("sys:pdm:change:add")
    @AuditLog(operation = "新建变更申请", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<PdmChgRequestVO> create(@Valid @RequestBody PdmChgRequestSaveRequest request,
                                          HttpServletRequest httpRequest) {
        return Result.ok(chgRequestService.create(request,
                PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 修改草稿变更申请
     *
     * @param id      申请ID
     * @param request 保存请求
     * @return 申请详情
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pdm:change:edit")
    @AuditLog(operation = "修改变更申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmChgRequestVO> update(@PathVariable Long id,
                                          @Valid @RequestBody PdmChgRequestSaveRequest request) {
        return Result.ok(chgRequestService.update(id, request));
    }

    /**
     * 删除草稿变更申请
     *
     * @param id 申请ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:change:delete")
    @AuditLog(operation = "删除变更申请", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        chgRequestService.delete(id);
        return Result.ok();
    }

    /**
     * 提交变更申请
     *
     * @param id 申请ID
     * @return 申请详情
     */
    @PostMapping("/{id}/submit")
    @RequirePermission("sys:pdm:change:submit")
    @AuditLog(operation = "提交变更申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmChgRequestVO> submit(@PathVariable Long id) {
        return Result.ok(chgRequestService.submit(id));
    }

    /**
     * 审批通过
     *
     * @param id      申请ID
     * @param request 审批请求
     * @param httpRequest 当前请求
     * @return 申请详情
     */
    @PostMapping("/{id}/approve")
    @RequirePermission("sys:pdm:change:approve")
    @AuditLog(operation = "审批通过变更申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmChgRequestVO> approve(@PathVariable Long id,
                                           @RequestBody(required = false) PdmChgApproveRequest request,
                                           HttpServletRequest httpRequest) {
        return Result.ok(chgRequestService.approve(id, commentOf(request),
                PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 审批驳回
     *
     * @param id      申请ID
     * @param request 审批请求
     * @param httpRequest 当前请求
     * @return 申请详情
     */
    @PostMapping("/{id}/reject")
    @RequirePermission("sys:pdm:change:approve")
    @AuditLog(operation = "驳回变更申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmChgRequestVO> reject(@PathVariable Long id,
                                          @RequestBody(required = false) PdmChgApproveRequest request,
                                          HttpServletRequest httpRequest) {
        return Result.ok(chgRequestService.reject(id, commentOf(request),
                PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 关闭变更申请
     *
     * @param id 申请ID
     * @return 申请详情
     */
    @PostMapping("/{id}/close")
    @RequirePermission("sys:pdm:change:edit")
    @AuditLog(operation = "关闭变更申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmChgRequestVO> close(@PathVariable Long id) {
        return Result.ok(chgRequestService.close(id));
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
