package com.bluewhale.iot.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.iot.dto.ProductSaveRequest;
import com.bluewhale.iot.dto.ProductVO;
import com.bluewhale.iot.service.IotProductService;
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
 * 产品接口：创建/更新/发布/停用/删除与分页查询（管理视角）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/iot/v{version}/products", version = "1")
@RequiredArgsConstructor
public class IotProductController {

    private final IotProductService productService;

    /**
     * 创建产品（草稿态）。
     *
     * @param request     保存请求
     * @param httpRequest 当前请求
     * @return 视图对象
     */
    @PostMapping
    @RequirePermission("sys:iot:product")
    @AuditLog(operation = "创建IoT产品", type = OperateType.CREATE, targetType = "iot_product")
    public Result<ProductVO> create(@Valid @RequestBody ProductSaveRequest request,
                                    HttpServletRequest httpRequest) {
        return Result.ok(productService.create(request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 产品分页。
     *
     * @param keyword 名称/编码模糊过滤（可空）
     * @param status  状态过滤（可空：draft/published/disabled）
     * @param current 当前页
     * @param size    每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:iot:product")
    public Result<PageResult<ProductVO>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        return Result.ok(productService.page(keyword, status, current, size));
    }

    /**
     * 产品详情。
     *
     * @param id 产品ID
     * @return 视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:iot:product")
    public Result<ProductVO> get(@PathVariable("id") Long id) {
        return Result.ok(productService.get(id));
    }

    /**
     * 更新产品（编码不可改；已发布产品需先停用）。
     *
     * @param id      产品ID
     * @param request 保存请求
     * @return 视图对象
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:iot:product")
    @AuditLog(operation = "更新IoT产品", type = OperateType.UPDATE, targetType = "iot_product")
    public Result<ProductVO> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody ProductSaveRequest request) {
        return Result.ok(productService.update(id, request));
    }

    /**
     * 发布产品。
     *
     * @param id 产品ID
     * @return 视图对象
     */
    @PostMapping("/{id}/publish")
    @RequirePermission("sys:iot:product")
    @AuditLog(operation = "发布IoT产品", type = OperateType.UPDATE, targetType = "iot_product")
    public Result<ProductVO> publish(@PathVariable("id") Long id) {
        return Result.ok(productService.publish(id));
    }

    /**
     * 停用产品（设备接入认证随即 fail-closed）。
     *
     * @param id 产品ID
     * @return 视图对象
     */
    @PostMapping("/{id}/disable")
    @RequirePermission("sys:iot:product")
    @AuditLog(operation = "停用IoT产品", type = OperateType.UPDATE, targetType = "iot_product")
    public Result<ProductVO> disable(@PathVariable("id") Long id) {
        return Result.ok(productService.disable(id));
    }

    /**
     * 删除产品（逻辑删除；产品下需无设备）。
     *
     * @param id 产品ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:iot:product")
    @AuditLog(operation = "删除IoT产品", type = OperateType.DELETE, targetType = "iot_product")
    public Result<Void> delete(@PathVariable("id") Long id) {
        productService.delete(id);
        return Result.ok();
    }
}
