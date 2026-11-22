package com.bluewhale.erp.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.dto.ErpItemQueryRequest;
import com.bluewhale.erp.dto.ErpItemSaveRequest;
import com.bluewhale.erp.service.ErpItemService;
import com.bluewhale.erp.vo.ErpItemVO;
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
 * ERP 物料接口
 *
 * <p>服务内路径 {@code /erp/item/**}，经网关按 {@code Path=/erp/**} 转发；
 * 全部接口受 {@code sys:erp:item:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/item", version = "1")
@RequiredArgsConstructor
public class ErpItemController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "erp_item";

    private final ErpItemService erpItemService;

    /**
     * 分页查询物料
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:item:list")
    public Result<PageResult<ErpItemVO>> page(ErpItemQueryRequest request) {
        return Result.ok(erpItemService.page(request));
    }

    /**
     * 物料详情
     *
     * @param id 主键ID
     * @return 物料详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:item:list")
    public Result<ErpItemVO> detail(@PathVariable Long id) {
        return Result.ok(erpItemService.getDetail(id));
    }

    /**
     * 新增物料
     *
     * @param request 新增请求
     * @return 新增后的物料
     */
    @PostMapping
    @RequirePermission("sys:erp:item:add")
    @AuditLog(operation = "新增物料", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ErpItemVO> create(@Valid @RequestBody ErpItemSaveRequest request) {
        return Result.ok(erpItemService.create(request));
    }

    /**
     * 更新物料
     *
     * @param id      主键ID
     * @param request 更新请求
     * @return 更新后的物料
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:erp:item:edit")
    @AuditLog(operation = "更新物料", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ErpItemVO> update(@PathVariable Long id, @Valid @RequestBody ErpItemSaveRequest request) {
        return Result.ok(erpItemService.update(id, request));
    }

    /**
     * 删除物料（逻辑删除）
     *
     * @param id 主键ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:erp:item:delete")
    @AuditLog(operation = "删除物料", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        erpItemService.delete(id);
        return Result.ok();
    }
}
