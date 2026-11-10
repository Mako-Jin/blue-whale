package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.catalog.application.command.SpuSaveCommand;
import com.bluewhale.mall.catalog.application.service.ProductAppService;
import com.bluewhale.mall.catalog.application.view.SpuDetailView;
import com.bluewhale.mall.catalog.application.view.SpuView;
import com.bluewhale.mall.catalog.domain.model.SpuQuery;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.interfaces.support.MallActorContext;
import com.bluewhale.mall.interfaces.support.PageParams;
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

/**
 * 商品接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mall/product")
@RequiredArgsConstructor
public class MallProductController {

    /** 单页上限 */
    private static final int PAGE_LIMIT = 100;

    private final ProductAppService productAppService;

    /**
     * 商品分页。
     *
     * @param keyword 关键字
     * @param categoryId 类目 ID
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(MallPermissions.PRODUCT_LIST)
    public Result<PageResult<SpuView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        SpuQuery query = new SpuQuery();
        query.setKeyword(keyword);
        query.setCategoryId(categoryId);
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize, PAGE_LIMIT));
        return Result.ok(productAppService.page(query, MallActorContext.from(request)));
    }

    /**
     * 商品详情。
     *
     * @param id 商品 ID
     * @param request 当前请求
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission(MallPermissions.PRODUCT_LIST)
    public Result<SpuDetailView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(productAppService.detail(id, MallActorContext.from(request)));
    }

    /**
     * 新建商品（草稿）。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 商品 ID
     */
    @PostMapping
    @RequirePermission(MallPermissions.PRODUCT_MANAGE)
    @AuditLog(operation = "新建商城商品", type = OperateType.CREATE, targetType = "mall_spu")
    public Result<Long> create(@Valid @RequestBody SpuSaveCommand command, HttpServletRequest request) {
        return Result.ok(productAppService.create(command, MallActorContext.from(request)));
    }

    /**
     * 编辑商品。
     *
     * @param id 商品 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 空结果
     */
    @PutMapping("/{id}")
    @RequirePermission(MallPermissions.PRODUCT_MANAGE)
    @AuditLog(operation = "编辑商城商品", type = OperateType.UPDATE, targetType = "mall_spu")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody SpuSaveCommand command,
                               HttpServletRequest request) {
        productAppService.update(id, command, MallActorContext.from(request));
        return Result.ok();
    }

    /**
     * 上架 / 下架。
     *
     * @param id 商品 ID
     * @param status 目标状态（ON_SALE / OFF_SALE）
     * @param request 当前请求
     * @return 空结果
     */
    @PutMapping("/{id}/status")
    @RequirePermission(MallPermissions.PRODUCT_PUBLISH)
    @AuditLog(operation = "商城商品上下架", type = OperateType.UPDATE, targetType = "mall_spu")
    public Result<Void> changeStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") String status,
                                     HttpServletRequest request) {
        productAppService.changeStatus(id, status, MallActorContext.from(request));
        return Result.ok();
    }

    /**
     * 删除商品。
     *
     * @param id 商品 ID
     * @param request 当前请求
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission(MallPermissions.PRODUCT_MANAGE)
    @AuditLog(operation = "删除商城商品", type = OperateType.DELETE, targetType = "mall_spu")
    public Result<Void> remove(@PathVariable("id") Long id, HttpServletRequest request) {
        productAppService.remove(id, MallActorContext.from(request));
        return Result.ok();
    }
}
