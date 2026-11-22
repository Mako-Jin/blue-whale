package com.bluewhale.archive.controller;

import com.bluewhale.archive.dto.ArchiveCategorySaveRequest;
import com.bluewhale.archive.service.ArchiveCategoryService;
import com.bluewhale.archive.vo.ArchiveCategoryVO;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
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
 * 档案门类管理接口。
 *
 * <p>路径前缀 {@code /archive/category}，权限码 {@code sys:archive:category:list} 与
 * {@code sys:archive:category:manage}；写操作均落审计。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/archive/v{version}/category", version = "1")
@RequiredArgsConstructor
public class ArchiveCategoryController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "archive_category";

    private final ArchiveCategoryService categoryService;

    /**
     * 查询门类树。
     *
     * @return 根节点列表
     */
    @GetMapping("/tree")
    @RequirePermission("sys:archive:category:list")
    public Result<List<ArchiveCategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    /**
     * 新增门类。
     *
     * @param request 保存请求
     * @return 新门类视图
     */
    @PostMapping
    @RequirePermission("sys:archive:category:manage")
    @AuditLog(operation = "新增档案门类", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ArchiveCategoryVO> create(@Valid @RequestBody ArchiveCategorySaveRequest request) {
        return Result.ok(categoryService.create(request));
    }

    /**
     * 修改门类。
     *
     * @param id 门类ID
     * @param request 保存请求
     * @return 修改后的门类视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:archive:category:manage")
    @AuditLog(operation = "修改档案门类", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ArchiveCategoryVO> update(@PathVariable Long id,
                                            @Valid @RequestBody ArchiveCategorySaveRequest request) {
        return Result.ok(categoryService.update(id, request));
    }

    /**
     * 删除门类。
     *
     * @param id 门类ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:archive:category:manage")
    @AuditLog(operation = "删除档案门类", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
