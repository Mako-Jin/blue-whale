package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.DeptAppService;
import com.bluewhale.auth.application.model.command.SaveDeptCommand;
import com.bluewhale.auth.interfaces.api.DeptApi;
import com.bluewhale.auth.interfaces.dto.request.DeptRequest;
import com.bluewhale.auth.interfaces.dto.response.DeptResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.DeptAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class DeptController implements DeptApi {

    private final DeptAppService deptAppService;

    private final DeptAssembler deptAssembler;

    /**
     * 部门树：parentId=0 为根，按 sort 升序
     *
     * @return 部门树节点列表
     */
    @Override
    @RequirePermission("sys:dept:list")
    public Result<List<DeptResponse>> tree() {
        return Result.ok(deptAssembler.toResponses(deptAppService.tree()));
    }

    /**
     * 部门详情
     *
     * @param id 部门 ID
     * @return 部门响应
     */
    @Override
    @RequirePermission("sys:dept:list")
    public Result<DeptResponse> detail(@PathVariable Long id) {
        return Result.ok(deptAssembler.toResponse(deptAppService.getDetail(id)));
    }

    /**
     * 创建部门
     *
     * @param request 部门请求参数
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:dept:add")
    @AuditLog(operation = "创建部门", type = OperateType.CREATE, targetType = "sys_dept")
    public Result<Void> create(@Valid @RequestBody DeptRequest request) {
        deptAppService.create(toCommand(request));
        return Result.ok();
    }

    /**
     * 更新部门
     *
     * @param id 部门 ID
     * @param request 部门请求参数
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:dept:edit")
    @AuditLog(operation = "更新部门", type = OperateType.UPDATE, targetType = "sys_dept", targetId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DeptRequest request) {
        deptAppService.update(id, toCommand(request));
        return Result.ok();
    }

    /**
     * 删除部门
     *
     * @param id 部门 ID
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:dept:delete")
    @AuditLog(operation = "删除部门", type = OperateType.DELETE, targetType = "sys_dept", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        deptAppService.delete(id);
        return Result.ok();
    }

    /**
     * 请求参数转换为应用层命令
     *
     * @param request 部门请求参数
     * @return 保存部门命令
     */
    private SaveDeptCommand toCommand(DeptRequest request) {
        return new SaveDeptCommand(request.getParentId(), request.getDeptName(), request.getSort(),
                request.getLeader(), request.getLeaderUserId());
    }
}
