package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.SaveRoleConstraintCommand;
import com.bluewhale.auth.application.model.query.ConstraintPageQuery;
import com.bluewhale.auth.application.service.RoleConstraintAppService;
import com.bluewhale.auth.interfaces.api.RoleConstraintApi;
import com.bluewhale.auth.interfaces.dto.request.RoleConstraintSaveRequest;
import com.bluewhale.auth.interfaces.dto.response.RoleConstraintResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.RoleConstraintAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色约束接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class RoleConstraintController implements RoleConstraintApi {

    private final RoleConstraintAppService roleConstraintAppService;
    private final RoleConstraintAssembler roleConstraintAssembler;

    /**
     * 分页查询角色约束
     *
     * @param pageQuery 分页参数
     * @param keyword 关键字
     * @param constraintType 类型过滤
     * @return 约束分页结果
     */
    @Override
    @RequirePermission("sys:role:constraint:list")
    public Result<PageResult<RoleConstraintResponse>> page(PageQuery pageQuery,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String constraintType) {
        return Result.ok(roleConstraintAssembler.toPage(roleConstraintAppService.page(new ConstraintPageQuery(
                pageQuery.getCurrent(), pageQuery.getSize(), keyword, constraintType))));
    }

    /**
     * 新增角色约束
     *
     * @param request 保存请求
     * @return 新建约束
     */
    @Override
    @RequirePermission("sys:role:constraint:add")
    @AuditLog(operation = "新增角色约束", type = OperateType.CREATE, targetType = "sys_role_constraint")
    public Result<RoleConstraintResponse> create(@Valid @RequestBody RoleConstraintSaveRequest request) {
        SaveRoleConstraintCommand command = roleConstraintAssembler.toCommand(request);
        return Result.ok(roleConstraintAssembler.toResponse(roleConstraintAppService.create(command)));
    }

    /**
     * 编辑角色约束（约束编码不可变）
     *
     * @param id 主键
     * @param request 保存请求
     * @return 更新后的约束
     */
    @Override
    @RequirePermission("sys:role:constraint:edit")
    @AuditLog(operation = "编辑角色约束", type = OperateType.UPDATE, targetType = "sys_role_constraint",
            targetId = "#id")
    public Result<RoleConstraintResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody RoleConstraintSaveRequest request) {
        SaveRoleConstraintCommand command = roleConstraintAssembler.toCommand(request);
        return Result.ok(roleConstraintAssembler.toResponse(roleConstraintAppService.update(id, command)));
    }

    /**
     * 删除角色约束
     *
     * @param id 主键
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:role:constraint:delete")
    @AuditLog(operation = "删除角色约束", type = OperateType.DELETE, targetType = "sys_role_constraint",
            targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        roleConstraintAppService.delete(id);
        return Result.ok();
    }
}
