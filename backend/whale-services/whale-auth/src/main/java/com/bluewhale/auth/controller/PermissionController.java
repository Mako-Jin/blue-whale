package com.bluewhale.auth.controller;

import com.bluewhale.auth.dto.PermissionRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.service.SysPermissionService;
import com.bluewhale.auth.vo.PermissionVO;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限点管理：CRUD + 分页
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final SysPermissionService permissionService;

    @GetMapping("/page")
    @RequirePermission("sys:permission:list")
    public Result<PageResult<PermissionVO>> page(PageQuery pageQuery,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String permType) {
        return Result.ok(permissionService.page(pageQuery.getCurrent(), pageQuery.getSize(), keyword, permType));
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:permission:list")
    public Result<PermissionVO> detail(@PathVariable Long id) {
        return Result.ok(permissionService.getDetail(id));
    }

    @PostMapping
    @RequirePermission("sys:permission:add")
    public Result<Void> create(@Valid @RequestBody PermissionRequest request) {
        permissionService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:permission:edit")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PermissionRequest request) {
        permissionService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:permission:delete")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return Result.ok();
    }
}
