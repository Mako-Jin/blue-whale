package com.bluewhale.auth.controller;

import com.bluewhale.auth.dto.DeptRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.service.SysDeptService;
import com.bluewhale.auth.vo.DeptVO;
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
 * 部门管理：CRUD + 树形查询
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/dept")
@RequiredArgsConstructor
public class DeptController {

    private final SysDeptService deptService;

    @GetMapping("/tree")
    @RequirePermission("sys:dept:list")
    public Result<List<DeptVO>> tree() {
        return Result.ok(deptService.tree());
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:dept:list")
    public Result<DeptVO> detail(@PathVariable Long id) {
        return Result.ok(deptService.getDetail(id));
    }

    @PostMapping
    @RequirePermission("sys:dept:add")
    public Result<Void> create(@Valid @RequestBody DeptRequest request) {
        deptService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:dept:edit")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DeptRequest request) {
        deptService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:dept:delete")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.ok();
    }
}
