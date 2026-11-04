package com.bluewhale.mes.equipment.service;

import com.bluewhale.common.core.result.PageResult;
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

/**
 * MES 设备应用服务
 *
 * <p>覆盖设备台账、状态事件与停机、保养计划/工单、维修工单、备件库存与 OEE 快照。
 * 不变量：设备编码唯一；状态事件只追加；故障设备门禁阻断；保养项未全部有结论不可完工；
 * 备件库存 ≥ 0；维修完成需验证后关闭。</p>
 *
 * @author BlueWhale
 */
public interface MesEquipmentService {

    /**
     * 保存设备台账
     *
     * @param request 保存请求
     * @return 设备视图
     */
    MesEquipmentVO saveEquipment(MesEquipmentSaveRequest request);

    /**
     * 分页查询设备台账
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesEquipmentVO> pageEquipments(MesEquipmentQueryRequest request);

    /**
     * 查询设备详情
     *
     * @param id 设备 ID
     * @return 设备视图
     */
    MesEquipmentVO getEquipment(Long id);

    /**
     * 设备开工门禁：故障/离线/停用设备阻断
     *
     * @param equipmentCode 设备编码
     * @return 门禁结果
     */
    MesEquipmentGateVO gate(String equipmentCode);

    /**
     * 变更设备状态（追加状态事件，故障自动开停机记录）
     *
     * @param id      设备 ID
     * @param request 状态变更请求
     * @return 状态事件视图
     */
    MesEquipmentStatusEventVO changeStatus(Long id, MesEquipmentStatusChangeRequest request);

    /**
     * 分页查询设备状态事件
     *
     * @param equipmentId 设备 ID（空则不限）
     * @param current     当前页
     * @param size        每页条数
     * @return 分页结果
     */
    PageResult<MesEquipmentStatusEventVO> pageStatusEvents(Long equipmentId, Long current, Long size);

    /**
     * 分页查询停机记录
     *
     * @param equipmentId 设备 ID（空则不限）
     * @param current     当前页
     * @param size        每页条数
     * @return 分页结果
     */
    PageResult<MesEquipmentDowntimeVO> pageDowntimes(Long equipmentId, Long current, Long size);

    /**
     * 保存保养计划
     *
     * @param request 保存请求
     * @return 计划视图
     */
    MesMaintenancePlanVO saveMaintenancePlan(MesMaintenancePlanSaveRequest request);

    /**
     * 分页查询保养计划
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesMaintenancePlanVO> pageMaintenancePlans(MesMaintenanceQueryRequest request);

    /**
     * 由保养计划生成保养工单
     *
     * @param request 创建请求
     * @return 保养工单视图
     */
    MesMaintenanceOrderVO createMaintenanceOrder(MesMaintenanceOrderCreateRequest request);

    /**
     * 分页查询保养工单
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesMaintenanceOrderVO> pageMaintenanceOrders(MesMaintenanceQueryRequest request);

    /**
     * 开始保养（PLANNED/DUE/OVERDUE → IN_PROGRESS）
     *
     * @param id       保养工单 ID
     * @param assignee 执行人
     * @return 保养工单视图
     */
    MesMaintenanceOrderVO startMaintenanceOrder(Long id, String assignee);

    /**
     * 保养完工（保养项结论必须覆盖计划全部项）
     *
     * @param id      保养工单 ID
     * @param request 完工请求
     * @return 保养工单视图
     */
    MesMaintenanceOrderVO completeMaintenanceOrder(Long id, MesMaintenanceOrderCompleteRequest request);

    /**
     * 保养验收（COMPLETED → ACCEPTED，回写计划到期时间）
     *
     * @param id      保养工单 ID
     * @param request 验收请求
     * @return 保养工单视图
     */
    MesMaintenanceOrderVO acceptMaintenanceOrder(Long id, MesMaintenanceOrderAcceptRequest request);

    /**
     * 创建维修工单
     *
     * @param request 创建请求
     * @return 维修工单视图
     */
    MesRepairOrderVO createRepair(MesRepairCreateRequest request);

    /**
     * 分页查询维修工单
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesRepairOrderVO> pageRepairs(MesRepairQueryRequest request);

    /**
     * 维修派工（REPORTED → ASSIGNED）
     *
     * @param id      维修工单 ID
     * @param request 动作请求
     * @return 维修工单视图
     */
    MesRepairOrderVO assignRepair(Long id, MesRepairActionRequest request);

    /**
     * 开始维修（ASSIGNED → REPAIRING）
     *
     * @param id      维修工单 ID
     * @param request 动作请求
     * @return 维修工单视图
     */
    MesRepairOrderVO startRepair(Long id, MesRepairActionRequest request);

    /**
     * 维修验证（REPAIRING → VERIFIED）
     *
     * @param id      维修工单 ID
     * @param request 动作请求
     * @return 维修工单视图
     */
    MesRepairOrderVO verifyRepair(Long id, MesRepairActionRequest request);

    /**
     * 关闭维修（VERIFIED → CLOSED，回填停机时长）
     *
     * @param id      维修工单 ID
     * @param request 动作请求
     * @return 维修工单视图
     */
    MesRepairOrderVO closeRepair(Long id, MesRepairActionRequest request);

    /**
     * 保存备件主档
     *
     * @param request 保存请求
     * @return 备件视图
     */
    MesSparePartVO saveSparePart(MesSparePartSaveRequest request);

    /**
     * 分页查询备件主档
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesSparePartVO> pageSpareParts(MesSparePartQueryRequest request);

    /**
     * 分页查询备件库存
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesSparePartStockVO> pageSparePartStocks(MesSparePartQueryRequest request);

    /**
     * 分页查询备件流水
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesSparePartMovementVO> pageSparePartMovements(MesSparePartQueryRequest request);

    /**
     * 备件入库
     *
     * @param request 出入库请求
     * @return 流水视图
     */
    MesSparePartMovementVO stockIn(MesSparePartStockRequest request);

    /**
     * 备件领用（库存不足拒绝）
     *
     * @param request 出入库请求
     * @return 流水视图
     */
    MesSparePartMovementVO stockOut(MesSparePartStockRequest request);

    /**
     * 计算并保存 OEE 快照（同设备同班次覆盖）
     *
     * @param request 计算请求
     * @return 快照视图
     */
    MesOeeSnapshotVO computeOee(MesOeeSnapshotRequest request);

    /**
     * 分页查询 OEE 快照
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesOeeSnapshotVO> pageOee(MesOeeQueryRequest request);
}
