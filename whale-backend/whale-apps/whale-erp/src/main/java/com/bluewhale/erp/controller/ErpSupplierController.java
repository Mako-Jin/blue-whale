package com.bluewhale.erp.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.dto.ErpSupplierQueryRequest;
import com.bluewhale.erp.dto.ErpSupplierSaveRequest;
import com.bluewhale.erp.service.ErpSupplierService;
import com.bluewhale.erp.vo.ErpSupplierVO;
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
 * ERP 供应商主数据接口。
 *
 * <p>服务内路径 {@code /erp/supplier/**}，经网关按 {@code Path=/erp/**} 转发；
 * 全部接口受 {@code sys:erp:supplier:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/supplier")
@RequiredArgsConstructor
public class ErpSupplierController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "erp_supplier";

    private final ErpSupplierService erpSupplierService;

    /**
     * 分页查询供应商。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:supplier:list")
    public Result<PageResult<ErpSupplierVO>> page(ErpSupplierQueryRequest request) {
        return Result.ok(erpSupplierService.page(request));
    }

    /**
     * 供应商详情。
     *
     * @param id 主键ID
     * @return 供应商详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:supplier:list")
    public Result<ErpSupplierVO> detail(@PathVariable Long id) {
        return Result.ok(erpSupplierService.getDetail(id));
    }

    /**
     * 新增供应商。
     *
     * @param request 新增请求
     * @return 新增后的供应商
     */
    @PostMapping
    @RequirePermission("sys:erp:supplier:add")
    @AuditLog(operation = "新增供应商", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ErpSupplierVO> create(@Valid @RequestBody ErpSupplierSaveRequest request) {
        return Result.ok(erpSupplierService.create(request));
    }

    /**
     * 更新供应商。
     *
     * @param id      主键ID
     * @param request 更新请求
     * @return 更新后的供应商
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:erp:supplier:edit")
    @AuditLog(operation = "更新供应商", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ErpSupplierVO> update(@PathVariable Long id, @Valid @RequestBody ErpSupplierSaveRequest request) {
        return Result.ok(erpSupplierService.update(id, request));
    }

    /**
     * 删除供应商（逻辑删除）。
     *
     * @param id 主键ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:erp:supplier:delete")
    @AuditLog(operation = "删除供应商", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        erpSupplierService.delete(id);
        return Result.ok();
    }
}
