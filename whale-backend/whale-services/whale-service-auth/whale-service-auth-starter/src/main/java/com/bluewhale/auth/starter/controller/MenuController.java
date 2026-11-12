package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.MenuAppService;
import com.bluewhale.auth.application.model.command.SaveMenuCommand;
import com.bluewhale.auth.interfaces.api.MenuApi;
import com.bluewhale.auth.interfaces.dto.request.MenuRequest;
import com.bluewhale.auth.interfaces.dto.response.MenuResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.MenuAssembler;
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
 * 菜单接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class MenuController implements MenuApi {

    private final MenuAppService menuAppService;

    private final MenuAssembler menuAssembler;

    /**
     * 菜单树：parentId=0 为根，按 sort 升序
     *
     * @return 菜单树节点列表
     */
    @Override
    @RequirePermission("sys:menu:list")
    public Result<List<MenuResponse>> tree() {
        return Result.ok(menuAssembler.toResponses(menuAppService.tree()));
    }

    /**
     * 菜单详情
     *
     * @param id 菜单 ID
     * @return 菜单响应
     */
    @Override
    @RequirePermission("sys:menu:list")
    public Result<MenuResponse> detail(@PathVariable Long id) {
        return Result.ok(menuAssembler.toResponse(menuAppService.getDetail(id)));
    }

    /**
     * 创建菜单
     *
     * @param request 菜单请求参数
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:menu:add")
    @AuditLog(operation = "创建菜单", type = OperateType.CREATE, targetType = "sys_menu")
    public Result<Void> create(@Valid @RequestBody MenuRequest request) {
        menuAppService.create(toCommand(request));
        return Result.ok();
    }

    /**
     * 更新菜单
     *
     * @param id 菜单 ID
     * @param request 菜单请求参数
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:menu:edit")
    @AuditLog(operation = "更新菜单", type = OperateType.UPDATE, targetType = "sys_menu", targetId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {
        menuAppService.update(id, toCommand(request));
        return Result.ok();
    }

    /**
     * 删除菜单
     *
     * @param id 菜单 ID
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:menu:delete")
    @AuditLog(operation = "删除菜单", type = OperateType.DELETE, targetType = "sys_menu", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        menuAppService.delete(id);
        return Result.ok();
    }

    /**
     * 请求参数转换为应用层命令
     *
     * @param request 菜单请求参数
     * @return 保存菜单命令
     */
    private SaveMenuCommand toCommand(MenuRequest request) {
        return new SaveMenuCommand(request.getParentId(), request.getName(), request.getPath(),
                request.getComponent(), request.getIcon(), request.getSort(), request.getMenuType(),
                request.getPermissionCode(), request.getVisible(), request.getStatus());
    }
}
