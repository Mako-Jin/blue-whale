package com.bluewhale.pdm.part.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.part.dto.PdmPartCategorySaveRequest;
import com.bluewhale.pdm.part.service.PdmPartCategoryService;
import com.bluewhale.pdm.part.vo.PdmPartCategoryVO;
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
 * PDM 物料分类接口
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/part/category")
@RequiredArgsConstructor
public class PdmPartCategoryController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "pdm_part_category";

    private final PdmPartCategoryService categoryService;

    /**
     * 分类树
     *
     * @return 分类树
     */
    @GetMapping("/tree")
    @RequirePermission("sys:pdm:part:list")
    public Result<List<PdmPartCategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    /**
     * 分类详情
     *
     * @param id 分类ID
     * @return 分类详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pdm:part:list")
    public Result<PdmPartCategoryVO> detail(@PathVariable Long id) {
        return Result.ok(categoryService.get(id));
    }

    /**
     * 新增分类
     *
     * @param request 保存请求
     * @return 分类视图
     */
    @PostMapping
    @RequirePermission("sys:pdm:part:category:manage")
    @AuditLog(operation = "新增物料分类", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<PdmPartCategoryVO> create(@Valid @RequestBody PdmPartCategorySaveRequest request) {
        return Result.ok(categoryService.create(request));
    }

    /**
     * 更新分类
     *
     * @param id      分类ID
     * @param request 保存请求
     * @return 分类视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pdm:part:category:manage")
    @AuditLog(operation = "更新物料分类", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmPartCategoryVO> update(@PathVariable Long id,
                                            @Valid @RequestBody PdmPartCategorySaveRequest request) {
        return Result.ok(categoryService.update(id, request));
    }

    /**
     * 删除分类
     *
     * @param id 分类ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:part:category:manage")
    @AuditLog(operation = "删除物料分类", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
