package com.bluewhale.mes.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.dto.MesWorkOrderQueryRequest;
import com.bluewhale.mes.dto.MesWorkOrderSaveRequest;
import com.bluewhale.mes.service.MesWorkOrderService;
import com.bluewhale.mes.vo.MesWorkOrderVO;
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
 * MES 生产工单接口
 *
 * <p>服务内路径 {@code /mes/work-order/**}，经网关按 {@code Path=/mes/**} 转发；
 * 全部接口受 {@code sys:mes:work-order:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mes/work-order")
@RequiredArgsConstructor
public class MesWorkOrderController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_work_order";

    private final MesWorkOrderService mesWorkOrderService;

    /**
     * 分页查询生产工单
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:mes:work-order:list")
    public Result<PageResult<MesWorkOrderVO>> page(MesWorkOrderQueryRequest request) {
        return Result.ok(mesWorkOrderService.page(request));
    }

    /**
     * 生产工单详情
     *
     * @param id 主键ID
     * @return 工单详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:mes:work-order:list")
    public Result<MesWorkOrderVO> detail(@PathVariable Long id) {
        return Result.ok(mesWorkOrderService.getDetail(id));
    }

    /**
     * 新增生产工单
     *
     * @param request 新增请求
     * @return 新增后的工单
     */
    @PostMapping
    @RequirePermission("sys:mes:work-order:add")
    @AuditLog(operation = "新增生产工单", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesWorkOrderVO> create(@Valid @RequestBody MesWorkOrderSaveRequest request) {
        return Result.ok(mesWorkOrderService.create(request));
    }

    /**
     * 更新生产工单
     *
     * @param id      主键ID
     * @param request 更新请求
     * @return 更新后的工单
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:mes:work-order:edit")
    @AuditLog(operation = "更新生产工单", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesWorkOrderVO> update(@PathVariable Long id, @Valid @RequestBody MesWorkOrderSaveRequest request) {
        return Result.ok(mesWorkOrderService.update(id, request));
    }

    /**
     * 删除生产工单（逻辑删除）
     *
     * @param id 主键ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:mes:work-order:delete")
    @AuditLog(operation = "删除生产工单", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        mesWorkOrderService.delete(id);
        return Result.ok();
    }
}
