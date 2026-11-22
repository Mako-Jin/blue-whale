package com.bluewhale.pdm.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.dto.PdmProductQueryRequest;
import com.bluewhale.pdm.dto.PdmProductSaveRequest;
import com.bluewhale.pdm.service.PdmProductService;
import com.bluewhale.pdm.vo.PdmProductVO;
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

/**
 * PDM 产品接口
 *
 * <p>服务内路径 {@code /pdm/product/**}，经网关按 {@code Path=/pdm/**} 转发；
 * 全部接口受 {@code sys:pdm:product:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pdm/v{version}/product", version = "1")
@RequiredArgsConstructor
public class PdmProductController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "pdm_product";

    private final PdmProductService pdmProductService;

    /**
     * 分页查询产品
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:product:list")
    public Result<PageResult<PdmProductVO>> page(PdmProductQueryRequest request) {
        return Result.ok(pdmProductService.page(request));
    }

    /**
     * 产品详情
     *
     * @param id 主键ID
     * @return 产品详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pdm:product:list")
    public Result<PdmProductVO> detail(@PathVariable Long id) {
        return Result.ok(pdmProductService.getDetail(id));
    }

    /**
     * 新增产品
     *
     * @param request 新增请求
     * @return 新增后的产品
     */
    @PostMapping
    @RequirePermission("sys:pdm:product:add")
    @AuditLog(operation = "新增产品", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<PdmProductVO> create(@Valid @RequestBody PdmProductSaveRequest request) {
        return Result.ok(pdmProductService.create(request));
    }

    /**
     * 更新产品
     *
     * @param id      主键ID
     * @param request 更新请求
     * @return 更新后的产品
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pdm:product:edit")
    @AuditLog(operation = "更新产品", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmProductVO> update(@PathVariable Long id, @Valid @RequestBody PdmProductSaveRequest request) {
        return Result.ok(pdmProductService.update(id, request));
    }

    /**
     * 删除产品（逻辑删除）
     *
     * @param id 主键ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:product:delete")
    @AuditLog(operation = "删除产品", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        pdmProductService.delete(id);
        return Result.ok();
    }
}
