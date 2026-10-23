package com.bluewhale.auth.controller;

import com.bluewhale.auth.application.menu.MenuAppService;
import com.bluewhale.auth.application.menu.command.SaveMenuCommand;
import com.bluewhale.auth.config.DddMigrationProperties;
import com.bluewhale.auth.dto.MenuRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.service.SysMenuService;
import com.bluewhale.auth.vo.MenuVO;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
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

import java.util.List;

/**
 * 菜单管理：CRUD + 树形查询
 *
 * <p>迁移期双路径：{@code whale.auth.ddd.menu=true} 时走 {@code application} 层 AppService，
 * 否则走既有 {@code SysMenuService}；REST 路径与出入参保持不变。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuController {

    private final SysMenuService menuService;
    private final MenuAppService menuAppService;
    private final DddMigrationProperties dddMigrationProperties;

    /**
     * 菜单树：parentId=0 为根，按 sort 升序
     *
     * @return 菜单树节点列表
     */
    @GetMapping("/tree")
    @RequirePermission("sys:menu:list")
    public Result<List<MenuVO>> tree() {
        return Result.ok(dddMigrationProperties.isMenu() ? menuAppService.tree() : menuService.tree());
    }

    /**
     * 菜单详情
     *
     * @param id 菜单 ID
     * @return 菜单视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:menu:list")
    public Result<MenuVO> detail(@PathVariable Long id) {
        return Result.ok(dddMigrationProperties.isMenu() ? menuAppService.getDetail(id) : menuService.getDetail(id));
    }

    /**
     * 创建菜单
     *
     * @param request 菜单请求参数
     * @return 操作结果
     */
    @PostMapping
    @RequirePermission("sys:menu:add")
    @AuditLog(operation = "创建菜单", type = OperateType.CREATE, targetType = "sys_menu")
    public Result<Void> create(@Valid @RequestBody MenuRequest request) {
        if (dddMigrationProperties.isMenu()) {
            menuAppService.create(toCommand(request));
        } else {
            menuService.create(request);
        }
        return Result.ok();
    }

    /**
     * 更新菜单
     *
     * @param id 菜单 ID
     * @param request 菜单请求参数
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:menu:edit")
    @AuditLog(operation = "更新菜单", type = OperateType.UPDATE, targetType = "sys_menu", targetId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {
        if (dddMigrationProperties.isMenu()) {
            menuAppService.update(id, toCommand(request));
        } else {
            menuService.update(id, request);
        }
        return Result.ok();
    }

    /**
     * 删除菜单
     *
     * @param id 菜单 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:menu:delete")
    @AuditLog(operation = "删除菜单", type = OperateType.DELETE, targetType = "sys_menu", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        if (dddMigrationProperties.isMenu()) {
            menuAppService.delete(id);
        } else {
            menuService.delete(id);
        }
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
