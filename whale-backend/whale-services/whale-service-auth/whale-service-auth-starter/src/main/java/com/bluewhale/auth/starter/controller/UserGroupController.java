package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.SaveUserGroupCommand;
import com.bluewhale.auth.application.model.query.UserGroupPageQuery;
import com.bluewhale.auth.application.service.UserGroupAppService;
import com.bluewhale.auth.interfaces.api.UserGroupApi;
import com.bluewhale.auth.interfaces.dto.request.UserGroupMembersSaveRequest;
import com.bluewhale.auth.interfaces.dto.request.UserGroupSaveRequest;
import com.bluewhale.auth.interfaces.dto.response.UserGroupResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.UserGroupAssembler;
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

import java.util.List;

/**
 * 用户组接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class UserGroupController implements UserGroupApi {

    private final UserGroupAppService userGroupAppService;
    private final UserGroupAssembler userGroupAssembler;

    /**
     * 分页查询用户组
     *
     * @param pageQuery 分页参数
     * @param keyword 关键字
     * @param category 类别过滤
     * @return 用户组分页结果
     */
    @Override
    @RequirePermission("sys:user:group:list")
    public Result<PageResult<UserGroupResponse>> page(PageQuery pageQuery,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String category) {
        return Result.ok(userGroupAssembler.toPage(userGroupAppService.page(new UserGroupPageQuery(
                pageQuery.getCurrent(), pageQuery.getSize(), keyword, category))));
    }

    /**
     * 新增用户组
     *
     * @param request 保存请求
     * @return 新建用户组
     */
    @Override
    @RequirePermission("sys:user:group:add")
    @AuditLog(operation = "新增用户组", type = OperateType.CREATE, targetType = "sys_user_group")
    public Result<UserGroupResponse> create(@Valid @RequestBody UserGroupSaveRequest request) {
        SaveUserGroupCommand command = userGroupAssembler.toCommand(request);
        return Result.ok(userGroupAssembler.toResponse(userGroupAppService.create(command)));
    }

    /**
     * 编辑用户组（组编码不可变）
     *
     * @param id 主键
     * @param request 保存请求
     * @return 更新后的用户组
     */
    @Override
    @RequirePermission("sys:user:group:edit")
    @AuditLog(operation = "编辑用户组", type = OperateType.UPDATE, targetType = "sys_user_group",
            targetId = "#id")
    public Result<UserGroupResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody UserGroupSaveRequest request) {
        SaveUserGroupCommand command = userGroupAssembler.toCommand(request);
        return Result.ok(userGroupAssembler.toResponse(userGroupAppService.update(id, command)));
    }

    /**
     * 删除用户组（并清理成员关系）
     *
     * @param id 主键
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:user:group:delete")
    @AuditLog(operation = "删除用户组", type = OperateType.DELETE, targetType = "sys_user_group",
            targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        userGroupAppService.delete(id);
        return Result.ok();
    }

    /**
     * 查询组成员用户 ID 列表
     *
     * @param id 组 ID
     * @return 成员用户 ID 列表
     */
    @Override
    @RequirePermission("sys:user:group:member")
    public Result<List<Long>> listMembers(@PathVariable Long id) {
        return Result.ok(userGroupAppService.listMemberUserIds(id));
    }

    /**
     * 全量替换静态组成员（空列表表示清空；动态组成员由规则求值重建，不可手工改）
     *
     * @param id 组 ID
     * @param request 成员保存请求
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:user:group:member")
    @AuditLog(operation = "替换用户组成员", type = OperateType.UPDATE, targetType = "sys_user_group",
            targetId = "#id")
    public Result<Void> replaceMembers(@PathVariable Long id,
                                       @Valid @RequestBody UserGroupMembersSaveRequest request) {
        userGroupAppService.replaceMembers(id, request.getMemberUserIds());
        return Result.ok();
    }
}
