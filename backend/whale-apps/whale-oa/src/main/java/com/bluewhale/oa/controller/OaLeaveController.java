package com.bluewhale.oa.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.dto.OaLeaveQueryRequest;
import com.bluewhale.oa.dto.OaLeaveSaveRequest;
import com.bluewhale.oa.dto.OaLeaveStatusRequest;
import com.bluewhale.oa.service.OaLeaveService;
import com.bluewhale.oa.vo.OaLeaveVO;
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
 * OA 请假申请接口
 *
 * <p>服务内路径 {@code /oa/leave/**}，经网关按 {@code Path=/oa/**} 转发；
 * 全部接口受 {@code sys:oa:leave:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/leave")
@RequiredArgsConstructor
public class OaLeaveController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_leave";

    private final OaLeaveService oaLeaveService;

    /**
     * 分页查询请假申请
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:leave:list")
    public Result<PageResult<OaLeaveVO>> page(OaLeaveQueryRequest request) {
        return Result.ok(oaLeaveService.page(request));
    }

    /**
     * 请假申请详情
     *
     * @param id 主键ID
     * @return 申请详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:leave:list")
    public Result<OaLeaveVO> detail(@PathVariable Long id) {
        return Result.ok(oaLeaveService.getDetail(id));
    }

    /**
     * 新增请假申请
     *
     * @param request 新增请求
     * @return 新增后的申请
     */
    @PostMapping
    @RequirePermission("sys:oa:leave:add")
    @AuditLog(operation = "新增请假申请", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<OaLeaveVO> create(@Valid @RequestBody OaLeaveSaveRequest request) {
        return Result.ok(oaLeaveService.create(request));
    }

    /**
     * 更新请假申请
     *
     * @param id      主键ID
     * @param request 更新请求
     * @return 更新后的申请
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:leave:edit")
    @AuditLog(operation = "更新请假申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<OaLeaveVO> update(@PathVariable Long id, @Valid @RequestBody OaLeaveSaveRequest request) {
        return Result.ok(oaLeaveService.update(id, request));
    }

    /**
     * 审批 / 撤销请假申请
     *
     * @param id      主键ID
     * @param request 状态变更请求
     * @return 空响应体
     */
    @PutMapping("/{id}/status")
    @RequirePermission("sys:oa:leave:edit")
    @AuditLog(operation = "审批请假申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody OaLeaveStatusRequest request) {
        oaLeaveService.updateStatus(id, request.getStatus());
        return Result.ok();
    }

    /**
     * 删除请假申请（逻辑删除）
     *
     * @param id 主键ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:leave:delete")
    @AuditLog(operation = "删除请假申请", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        oaLeaveService.delete(id);
        return Result.ok();
    }
}
