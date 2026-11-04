package com.bluewhale.erp.qc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.qc.application.command.EquipmentSaveCommand;
import com.bluewhale.erp.qc.application.command.MaintenanceCompleteCommand;
import com.bluewhale.erp.qc.application.command.MaintenanceOrderCreateCommand;
import com.bluewhale.erp.qc.application.command.MaintenancePlanSaveCommand;
import com.bluewhale.erp.qc.application.command.OeeRecordCommand;
import com.bluewhale.erp.qc.application.service.EquipmentAppService;
import com.bluewhale.erp.qc.application.view.EquipmentView;
import com.bluewhale.erp.qc.application.view.MaintenanceOrderView;
import com.bluewhale.erp.qc.application.view.MaintenancePlanView;
import com.bluewhale.erp.qc.application.view.OeeRecordView;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备与保养接口：设备档案、保养计划/工单与 OEE 记录。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/qc")
@RequiredArgsConstructor
public class QcEquipmentController {

    private final EquipmentAppService equipmentAppService;

    /**
     * 设备分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 设备分页
     */
    @GetMapping("/equipment/page")
    @RequirePermission("sys:erp:qc:equipment:list")
    public Result<PageResult<EquipmentView>> pageEquipment(
            @ModelAttribute QcQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(equipmentAppService.pageEquipment(query));
    }

    /**
     * 新增设备档案。
     *
     * @param command 保存命令
     * @return 设备
     */
    @PostMapping("/equipment")
    @RequirePermission("sys:erp:qc:equipment:add")
    @AuditLog(operation = "新增设备档案", targetType = "erp_qc_equipment", type = OperateType.CREATE)
    public Result<EquipmentView> createEquipment(@Valid @RequestBody EquipmentSaveCommand command) {
        return Result.ok(equipmentAppService.createEquipment(command));
    }

    /**
     * 编辑设备档案。
     *
     * @param equipmentNo 设备编码
     * @param command 保存命令
     * @return 设备
     */
    @PutMapping("/equipment/{equipmentNo}")
    @RequirePermission("sys:erp:qc:equipment:update")
    @AuditLog(operation = "编辑设备档案", targetType = "erp_qc_equipment", type = OperateType.UPDATE)
    public Result<EquipmentView> updateEquipment(@PathVariable String equipmentNo,
                                                 @Valid @RequestBody EquipmentSaveCommand command) {
        return Result.ok(equipmentAppService.updateEquipment(equipmentNo, command));
    }

    /**
     * 保养计划分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 保养计划分页
     */
    @GetMapping("/maintenance/plan/page")
    @RequirePermission("sys:erp:qc:maintenance:list")
    public Result<PageResult<MaintenancePlanView>> pagePlans(
            @ModelAttribute QcQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(equipmentAppService.pagePlans(query));
    }

    /**
     * 新增保养计划。
     *
     * @param command 保存命令
     * @return 保养计划
     */
    @PostMapping("/maintenance/plan")
    @RequirePermission("sys:erp:qc:maintenance:save")
    @AuditLog(operation = "新增保养计划", targetType = "erp_qc_maintenance_plan", type = OperateType.CREATE)
    public Result<MaintenancePlanView> createPlan(@Valid @RequestBody MaintenancePlanSaveCommand command) {
        return Result.ok(equipmentAppService.createPlan(command));
    }

    /**
     * 编辑保养计划。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 保养计划
     */
    @PutMapping("/maintenance/plan/{id}")
    @RequirePermission("sys:erp:qc:maintenance:save")
    @AuditLog(operation = "编辑保养计划", targetType = "erp_qc_maintenance_plan", type = OperateType.UPDATE)
    public Result<MaintenancePlanView> updatePlan(@PathVariable Long id,
                                                  @Valid @RequestBody MaintenancePlanSaveCommand command) {
        return Result.ok(equipmentAppService.updatePlan(id, command));
    }

    /**
     * 保养工单分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 保养工单分页
     */
    @GetMapping("/maintenance/order/page")
    @RequirePermission("sys:erp:qc:maintenance:list")
    public Result<PageResult<MaintenanceOrderView>> pageOrders(
            @ModelAttribute QcQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(equipmentAppService.pageOrders(query));
    }

    /**
     * 新增保养工单。
     *
     * @param command 创建命令
     * @return 保养工单
     */
    @PostMapping("/maintenance/order")
    @RequirePermission("sys:erp:qc:maintenance:save")
    @AuditLog(operation = "新增保养工单", targetType = "erp_qc_maintenance_order", type = OperateType.CREATE)
    public Result<MaintenanceOrderView> createOrder(@Valid @RequestBody MaintenanceOrderCreateCommand command) {
        return Result.ok(equipmentAppService.createOrder(command));
    }

    /**
     * 完成保养工单。
     *
     * @param id 主键
     * @param command 完成命令
     * @return 保养工单
     */
    @PostMapping("/maintenance/order/{id}/complete")
    @RequirePermission("sys:erp:qc:maintenance:complete")
    @AuditLog(operation = "完成保养工单", targetType = "erp_qc_maintenance_order", type = OperateType.UPDATE)
    public Result<MaintenanceOrderView> completeOrder(@PathVariable Long id,
                                                      @Valid @RequestBody MaintenanceCompleteCommand command) {
        return Result.ok(equipmentAppService.completeOrder(id, command));
    }

    /**
     * OEE 记录分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return OEE 分页
     */
    @GetMapping("/oee/page")
    @RequirePermission("sys:erp:qc:oee:list")
    public Result<PageResult<OeeRecordView>> pageOee(
            @ModelAttribute QcQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(equipmentAppService.pageOee(query));
    }

    /**
     * 记录/回传 OEE 数据（同设备同日期幂等覆盖）。
     *
     * @param command OEE 命令
     * @return OEE 记录
     */
    @PostMapping("/oee/record")
    @RequirePermission("sys:erp:qc:oee:record")
    @AuditLog(operation = "回传设备 OEE 数据", targetType = "erp_qc_oee_record", type = OperateType.CREATE)
    public Result<OeeRecordView> recordOee(@Valid @RequestBody OeeRecordCommand command) {
        return Result.ok(equipmentAppService.recordOee(command));
    }
}
