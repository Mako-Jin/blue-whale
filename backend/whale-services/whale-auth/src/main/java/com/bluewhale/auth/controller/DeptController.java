package com.bluewhale.auth.controller;

import com.bluewhale.auth.dto.DeptRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.service.SysDeptService;
import com.bluewhale.auth.vo.DeptVO;
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
 * 部门管理：CRUD + 树形查询
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/dept")
@RequiredArgsConstructor
public class DeptController {

    private final SysDeptService deptService;

    /**
     * 部门树：parentId=0 为根，按 sort 升序
     *
     * @return 部门树节点列表
     */
    @GetMapping("/tree")
    @RequirePermission("sys:dept:list")
    public Result<List<DeptVO>> tree() {
        return Result.ok(deptService.tree());
    }

    /**
     * 部门详情
     *
     * @param id 部门 ID
     * @return 部门视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:dept:list")
    public Result<DeptVO> detail(@PathVariable Long id) {
        return Result.ok(deptService.getDetail(id));
    }

    /**
     * 创建部门
     *
     * @param request 部门请求参数
     * @return 操作结果
     */
    @PostMapping
    @RequirePermission("sys:dept:add")
    @AuditLog(operation = "创建部门", type = OperateType.CREATE, targetType = "sys_dept")
    public Result<Void> create(@Valid @RequestBody DeptRequest request) {
        deptService.create(request);
        return Result.ok();
    }

    /**
     * 更新部门
     *
     * @param id 部门 ID
     * @param request 部门请求参数
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:dept:edit")
    @AuditLog(operation = "更新部门", type = OperateType.UPDATE, targetType = "sys_dept", targetId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DeptRequest request) {
        deptService.update(id, request);
        return Result.ok();
    }

    /**
     * 删除部门
     *
     * @param id 部门 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:dept:delete")
    @AuditLog(operation = "删除部门", type = OperateType.DELETE, targetType = "sys_dept", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.ok();
    }
}
