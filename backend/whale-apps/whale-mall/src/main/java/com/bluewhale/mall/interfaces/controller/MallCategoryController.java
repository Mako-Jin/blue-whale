package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.catalog.application.command.CategorySaveCommand;
import com.bluewhale.mall.catalog.application.service.CategoryAppService;
import com.bluewhale.mall.catalog.application.view.CategoryTreeView;
import com.bluewhale.mall.catalog.domain.model.CategoryQuery;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.interfaces.support.MallActorContext;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;

/**
 * 商品类目接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mall/category")
@RequiredArgsConstructor
public class MallCategoryController {

    private final CategoryAppService categoryAppService;

    /**
     * 类目树。
     *
     * @param keyword 关键字
     * @param status 状态
     * @param request 当前请求
     * @return 树根列表
     */
    @GetMapping("/tree")
    @RequirePermission(MallPermissions.CATEGORY_LIST)
    public Result<List<CategoryTreeView>> tree(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            HttpServletRequest request) {
        CategoryQuery query = new CategoryQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        return Result.ok(categoryAppService.tree(query, MallActorContext.from(request)));
    }

    /**
     * 新建类目。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 新建的类目节点
     */
    @PostMapping
    @RequirePermission(MallPermissions.CATEGORY_MANAGE)
    @AuditLog(operation = "新建商城类目", type = OperateType.CREATE, targetType = "mall_category")
    public Result<CategoryTreeView> create(@Valid @RequestBody CategorySaveCommand command,
                                           HttpServletRequest request) {
        return Result.ok(categoryAppService.create(command, MallActorContext.from(request)));
    }

    /**
     * 编辑类目。
     *
     * @param id 类目 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 编辑后的类目节点
     */
    @PutMapping("/{id}")
    @RequirePermission(MallPermissions.CATEGORY_MANAGE)
    @AuditLog(operation = "编辑商城类目", type = OperateType.UPDATE, targetType = "mall_category")
    public Result<CategoryTreeView> update(@PathVariable("id") Long id,
                                           @Valid @RequestBody CategorySaveCommand command,
                                           HttpServletRequest request) {
        return Result.ok(categoryAppService.update(id, command, MallActorContext.from(request)));
    }

    /**
     * 删除类目。
     *
     * @param id 类目 ID
     * @param request 当前请求
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission(MallPermissions.CATEGORY_MANAGE)
    @AuditLog(operation = "删除商城类目", type = OperateType.DELETE, targetType = "mall_category")
    public Result<Void> remove(@PathVariable("id") Long id, HttpServletRequest request) {
        categoryAppService.remove(id, MallActorContext.from(request));
        return Result.ok();
    }
}
