package com.bluewhale.mes.equipment.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.equipment.dto.MesEquipmentQueryRequest;
import com.bluewhale.mes.equipment.dto.MesEquipmentSaveRequest;
import com.bluewhale.mes.equipment.dto.MesEquipmentStatusChangeRequest;
import com.bluewhale.mes.equipment.dto.MesMaintenanceOrderAcceptRequest;
import com.bluewhale.mes.equipment.dto.MesMaintenanceOrderCompleteRequest;
import com.bluewhale.mes.equipment.dto.MesMaintenanceOrderCreateRequest;
import com.bluewhale.mes.equipment.dto.MesMaintenancePlanSaveRequest;
import com.bluewhale.mes.equipment.dto.MesMaintenanceQueryRequest;
import com.bluewhale.mes.equipment.dto.MesOeeQueryRequest;
import com.bluewhale.mes.equipment.dto.MesOeeSnapshotRequest;
import com.bluewhale.mes.equipment.dto.MesRepairActionRequest;
import com.bluewhale.mes.equipment.dto.MesRepairCreateRequest;
import com.bluewhale.mes.equipment.dto.MesRepairQueryRequest;
import com.bluewhale.mes.equipment.dto.MesSparePartQueryRequest;
import com.bluewhale.mes.equipment.dto.MesSparePartSaveRequest;
import com.bluewhale.mes.equipment.dto.MesSparePartStockRequest;
import com.bluewhale.mes.equipment.service.MesEquipmentService;
import com.bluewhale.mes.equipment.vo.MesEquipmentDowntimeVO;
import com.bluewhale.mes.equipment.vo.MesEquipmentGateVO;
import com.bluewhale.mes.equipment.vo.MesEquipmentStatusEventVO;
import com.bluewhale.mes.equipment.vo.MesEquipmentVO;
import com.bluewhale.mes.equipment.vo.MesMaintenanceOrderVO;
import com.bluewhale.mes.equipment.vo.MesMaintenancePlanVO;
import com.bluewhale.mes.equipment.vo.MesOeeSnapshotVO;
import com.bluewhale.mes.equipment.vo.MesRepairOrderVO;
import com.bluewhale.mes.equipment.vo.MesSparePartMovementVO;
import com.bluewhale.mes.equipment.vo.MesSparePartStockVO;
import com.bluewhale.mes.equipment.vo.MesSparePartVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 设备管理接口
 *
 * <p>覆盖设备台账与状态、保养、维修、备件与 OEE 六类能力。
 * {@code /gate} 为开工门禁：故障/离线/停用设备返回 {@code passed=false}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mes/v{version}/equipment", version = "1")
@RequiredArgsConstructor
public class MesEquipmentController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_equipment";

    private final MesEquipmentService equipmentService;

    /**
     * 保存设备台账
     *
     * @param request 保存请求
     * @return 设备视图
     */
    @PostMapping
    @RequirePermission("sys:mes:equipment:manage")
    @AuditLog(operation = "MES 设备台账保存", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesEquipmentVO> save(@Valid @RequestBody MesEquipmentSaveRequest request) {
        return Result.ok(equipmentService.saveEquipment(request));
    }

    /**
     * 分页查询设备台账
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesEquipmentVO>> page(MesEquipmentQueryRequest request) {
        return Result.ok(equipmentService.pageEquipments(request));
    }

    /**
     * 查询设备详情
     *
     * @param id 设备 ID
     * @return 设备视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:mes:equipment:list")
    public Result<MesEquipmentVO> detail(@PathVariable Long id) {
        return Result.ok(equipmentService.getEquipment(id));
    }

    /**
     * 设备开工门禁
     *
     * @param equipmentCode 设备编码
     * @return 门禁结果
     */
    @GetMapping("/gate")
    @RequirePermission("sys:mes:equipment:list")
    public Result<MesEquipmentGateVO> gate(@RequestParam String equipmentCode) {
        return Result.ok(equipmentService.gate(equipmentCode));
    }

    /**
     * 变更设备状态
     *
     * @param id      设备 ID
     * @param request 状态变更请求
     * @return 状态事件视图
     */
    @PostMapping("/{id}/status")
    @RequirePermission("sys:mes:equipment:status")
    @AuditLog(operation = "MES 设备状态变更", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesEquipmentStatusEventVO> changeStatus(@PathVariable Long id,
                                                          @Valid @RequestBody
                                                          MesEquipmentStatusChangeRequest request) {
        return Result.ok(equipmentService.changeStatus(id, request));
    }

    /**
     * 分页查询设备状态事件
     *
     * @param equipmentId 设备 ID（空则不限）
     * @param current     当前页
     * @param size        每页条数
     * @return 分页结果
     */
    @GetMapping("/status-event/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesEquipmentStatusEventVO>> pageStatusEvents(@RequestParam(required = false)
                                                                         Long equipmentId,
                                                                         @RequestParam(required = false)
                                                                         Long current,
                                                                         @RequestParam(required = false) Long size) {
        return Result.ok(equipmentService.pageStatusEvents(equipmentId, current, size));
    }

    /**
     * 分页查询停机记录
     *
     * @param equipmentId 设备 ID（空则不限）
     * @param current     当前页
     * @param size        每页条数
     * @return 分页结果
     */
    @GetMapping("/downtime/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesEquipmentDowntimeVO>> pageDowntimes(@RequestParam(required = false) Long equipmentId,
                                                                    @RequestParam(required = false) Long current,
                                                                    @RequestParam(required = false) Long size) {
        return Result.ok(equipmentService.pageDowntimes(equipmentId, current, size));
    }

    /**
     * 保存保养计划
     *
     * @param request 保存请求
     * @return 计划视图
     */
    @PostMapping("/maintenance/plan")
    @RequirePermission("sys:mes:equipment:maintenance")
    @AuditLog(operation = "MES 保养计划保存", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesMaintenancePlanVO> savePlan(@Valid @RequestBody MesMaintenancePlanSaveRequest request) {
        return Result.ok(equipmentService.saveMaintenancePlan(request));
    }

    /**
     * 分页查询保养计划
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/maintenance/plan/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesMaintenancePlanVO>> pagePlans(MesMaintenanceQueryRequest request) {
        return Result.ok(equipmentService.pageMaintenancePlans(request));
    }

    /**
     * 生成保养工单
     *
     * @param request 创建请求
     * @return 保养工单视图
     */
    @PostMapping("/maintenance/order")
    @RequirePermission("sys:mes:equipment:maintenance")
    @AuditLog(operation = "MES 保养工单创建", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesMaintenanceOrderVO> createOrder(@Valid @RequestBody MesMaintenanceOrderCreateRequest request) {
        return Result.ok(equipmentService.createMaintenanceOrder(request));
    }

    /**
     * 分页查询保养工单
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/maintenance/order/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesMaintenanceOrderVO>> pageOrders(MesMaintenanceQueryRequest request) {
        return Result.ok(equipmentService.pageMaintenanceOrders(request));
    }

    /**
     * 开始保养
     *
     * @param id       保养工单 ID
     * @param assignee 执行人
     * @return 保养工单视图
     */
    @PostMapping("/maintenance/order/{id}/start")
    @RequirePermission("sys:mes:equipment:maintenance")
    @AuditLog(operation = "MES 保养开始", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesMaintenanceOrderVO> startOrder(@PathVariable Long id,
                                                    @RequestParam(required = false) String assignee) {
        return Result.ok(equipmentService.startMaintenanceOrder(id, assignee));
    }

    /**
     * 保养完工
     *
     * @param id      保养工单 ID
     * @param request 完工请求
     * @return 保养工单视图
     */
    @PostMapping("/maintenance/order/{id}/complete")
    @RequirePermission("sys:mes:equipment:maintenance")
    @AuditLog(operation = "MES 保养完工", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesMaintenanceOrderVO> completeOrder(@PathVariable Long id,
                                                       @Valid @RequestBody
                                                       MesMaintenanceOrderCompleteRequest request) {
        return Result.ok(equipmentService.completeMaintenanceOrder(id, request));
    }

    /**
     * 保养验收
     *
     * @param id      保养工单 ID
     * @param request 验收请求
     * @return 保养工单视图
     */
    @PostMapping("/maintenance/order/{id}/accept")
    @RequirePermission("sys:mes:equipment:maintenance")
    @AuditLog(operation = "MES 保养验收", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesMaintenanceOrderVO> acceptOrder(@PathVariable Long id,
                                                     @Valid @RequestBody
                                                     MesMaintenanceOrderAcceptRequest request) {
        return Result.ok(equipmentService.acceptMaintenanceOrder(id, request));
    }

    /**
     * 创建维修工单
     *
     * @param request 创建请求
     * @return 维修工单视图
     */
    @PostMapping("/repair")
    @RequirePermission("sys:mes:equipment:repair")
    @AuditLog(operation = "MES 维修报修", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesRepairOrderVO> createRepair(@Valid @RequestBody MesRepairCreateRequest request) {
        return Result.ok(equipmentService.createRepair(request));
    }

    /**
     * 分页查询维修工单
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/repair/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesRepairOrderVO>> pageRepairs(MesRepairQueryRequest request) {
        return Result.ok(equipmentService.pageRepairs(request));
    }

    /**
     * 维修派工
     *
     * @param id      维修工单 ID
     * @param request 动作请求
     * @return 维修工单视图
     */
    @PostMapping("/repair/{id}/assign")
    @RequirePermission("sys:mes:equipment:repair")
    @AuditLog(operation = "MES 维修派工", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesRepairOrderVO> assignRepair(@PathVariable Long id,
                                                 @Valid @RequestBody MesRepairActionRequest request) {
        return Result.ok(equipmentService.assignRepair(id, request));
    }

    /**
     * 开始维修
     *
     * @param id      维修工单 ID
     * @param request 动作请求
     * @return 维修工单视图
     */
    @PostMapping("/repair/{id}/start")
    @RequirePermission("sys:mes:equipment:repair")
    @AuditLog(operation = "MES 维修开始", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesRepairOrderVO> startRepair(@PathVariable Long id,
                                                @Valid @RequestBody MesRepairActionRequest request) {
        return Result.ok(equipmentService.startRepair(id, request));
    }

    /**
     * 维修验证
     *
     * @param id      维修工单 ID
     * @param request 动作请求
     * @return 维修工单视图
     */
    @PostMapping("/repair/{id}/verify")
    @RequirePermission("sys:mes:equipment:repair")
    @AuditLog(operation = "MES 维修验证", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesRepairOrderVO> verifyRepair(@PathVariable Long id,
                                                 @Valid @RequestBody MesRepairActionRequest request) {
        return Result.ok(equipmentService.verifyRepair(id, request));
    }

    /**
     * 关闭维修
     *
     * @param id      维修工单 ID
     * @param request 动作请求
     * @return 维修工单视图
     */
    @PostMapping("/repair/{id}/close")
    @RequirePermission("sys:mes:equipment:repair")
    @AuditLog(operation = "MES 维修关闭", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesRepairOrderVO> closeRepair(@PathVariable Long id,
                                                @Valid @RequestBody MesRepairActionRequest request) {
        return Result.ok(equipmentService.closeRepair(id, request));
    }

    /**
     * 保存备件主档
     *
     * @param request 保存请求
     * @return 备件视图
     */
    @PostMapping("/spare-part")
    @RequirePermission("sys:mes:equipment:spare-part")
    @AuditLog(operation = "MES 备件保存", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesSparePartVO> saveSparePart(@Valid @RequestBody MesSparePartSaveRequest request) {
        return Result.ok(equipmentService.saveSparePart(request));
    }

    /**
     * 分页查询备件主档
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/spare-part/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesSparePartVO>> pageSpareParts(MesSparePartQueryRequest request) {
        return Result.ok(equipmentService.pageSpareParts(request));
    }

    /**
     * 分页查询备件库存
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/spare-part/stock/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesSparePartStockVO>> pageSparePartStocks(MesSparePartQueryRequest request) {
        return Result.ok(equipmentService.pageSparePartStocks(request));
    }

    /**
     * 分页查询备件流水
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/spare-part/movement/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesSparePartMovementVO>> pageSparePartMovements(MesSparePartQueryRequest request) {
        return Result.ok(equipmentService.pageSparePartMovements(request));
    }

    /**
     * 备件入库
     *
     * @param request 出入库请求
     * @return 流水视图
     */
    @PostMapping("/spare-part/stock/in")
    @RequirePermission("sys:mes:equipment:spare-part")
    @AuditLog(operation = "MES 备件入库", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesSparePartMovementVO> stockIn(@Valid @RequestBody MesSparePartStockRequest request) {
        return Result.ok(equipmentService.stockIn(request));
    }

    /**
     * 备件领用
     *
     * @param request 出入库请求
     * @return 流水视图
     */
    @PostMapping("/spare-part/stock/out")
    @RequirePermission("sys:mes:equipment:spare-part")
    @AuditLog(operation = "MES 备件领用", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesSparePartMovementVO> stockOut(@Valid @RequestBody MesSparePartStockRequest request) {
        return Result.ok(equipmentService.stockOut(request));
    }

    /**
     * 计算 OEE 快照
     *
     * @param request 计算请求
     * @return 快照视图
     */
    @PostMapping("/oee/snapshot")
    @RequirePermission("sys:mes:equipment:oee")
    @AuditLog(operation = "MES OEE 快照计算", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesOeeSnapshotVO> computeOee(@Valid @RequestBody MesOeeSnapshotRequest request) {
        return Result.ok(equipmentService.computeOee(request));
    }

    /**
     * 分页查询 OEE 快照
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/oee/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesOeeSnapshotVO>> pageOee(MesOeeQueryRequest request) {
        return Result.ok(equipmentService.pageOee(request));
    }
}
