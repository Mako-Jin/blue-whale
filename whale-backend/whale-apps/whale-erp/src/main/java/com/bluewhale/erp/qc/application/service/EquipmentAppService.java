package com.bluewhale.erp.qc.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.qc.application.command.EquipmentSaveCommand;
import com.bluewhale.erp.qc.application.command.MaintenanceCompleteCommand;
import com.bluewhale.erp.qc.application.command.MaintenanceOrderCreateCommand;
import com.bluewhale.erp.qc.application.command.MaintenancePlanSaveCommand;
import com.bluewhale.erp.qc.application.command.OeeRecordCommand;
import com.bluewhale.erp.qc.application.view.EquipmentView;
import com.bluewhale.erp.qc.application.view.MaintenanceOrderView;
import com.bluewhale.erp.qc.application.view.MaintenancePlanView;
import com.bluewhale.erp.qc.application.view.OeeRecordView;
import com.bluewhale.erp.qc.domain.model.QcQuery;

/**
 * 设备、保养与 OEE 应用服务。
 *
 * @author BlueWhale
 */
public interface EquipmentAppService {

    /**
     * 分页查询设备。
     *
     * @param query 查询条件
     * @return 设备分页
     */
    PageResult<EquipmentView> pageEquipment(QcQuery query);

    /**
     * 新增设备档案。
     *
     * @param command 保存命令
     * @return 设备
     */
    EquipmentView createEquipment(EquipmentSaveCommand command);

    /**
     * 编辑设备档案。
     *
     * @param equipmentNo 设备编码
     * @param command 保存命令
     * @return 设备
     */
    EquipmentView updateEquipment(String equipmentNo, EquipmentSaveCommand command);

    /**
     * 分页查询保养计划。
     *
     * @param query 查询条件
     * @return 保养计划分页
     */
    PageResult<MaintenancePlanView> pagePlans(QcQuery query);

    /**
     * 新增保养计划。
     *
     * @param command 保存命令
     * @return 保养计划
     */
    MaintenancePlanView createPlan(MaintenancePlanSaveCommand command);

    /**
     * 编辑保养计划。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 保养计划
     */
    MaintenancePlanView updatePlan(Long id, MaintenancePlanSaveCommand command);

    /**
     * 分页查询保养工单。
     *
     * @param query 查询条件
     * @return 保养工单分页
     */
    PageResult<MaintenanceOrderView> pageOrders(QcQuery query);

    /**
     * 新增保养工单。
     *
     * @param command 创建命令
     * @return 保养工单
     */
    MaintenanceOrderView createOrder(MaintenanceOrderCreateCommand command);

    /**
     * 完成保养工单并推进保养计划。
     *
     * @param id 主键
     * @param command 完成命令
     * @return 保养工单
     */
    MaintenanceOrderView completeOrder(Long id, MaintenanceCompleteCommand command);

    /**
     * 分页查询 OEE 记录。
     *
     * @param query 查询条件
     * @return OEE 分页
     */
    PageResult<OeeRecordView> pageOee(QcQuery query);

    /**
     * 记录 OEE 数据（同设备同日期幂等覆盖）。
     *
     * @param command OEE 命令
     * @return OEE 记录
     */
    OeeRecordView recordOee(OeeRecordCommand command);
}
