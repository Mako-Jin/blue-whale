package com.bluewhale.erp.qc.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
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
import com.bluewhale.erp.qc.domain.model.Equipment;
import com.bluewhale.erp.qc.domain.model.MaintenanceOrder;
import com.bluewhale.erp.qc.domain.model.MaintenancePlan;
import com.bluewhale.erp.qc.domain.model.OeeRecord;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import com.bluewhale.erp.qc.domain.model.QcStatus;
import com.bluewhale.erp.qc.domain.repository.EquipmentRepository;
import com.bluewhale.erp.qc.domain.repository.MaintenanceRepository;
import com.bluewhale.erp.qc.domain.repository.OeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 设备、保养与 OEE 应用服务实现。
 *
 * <p>保养工单完成后回写计划的上次/下次保养日期；OEE 按「设备 + 统计日期」幂等覆盖，
 * 便于 MES 重复回传不产生重复记录。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class EquipmentAppServiceImpl implements EquipmentAppService {

    private final EquipmentRepository equipmentRepository;

    private final MaintenanceRepository maintenanceRepository;

    private final OeeRepository oeeRepository;

    @Override
    public PageResult<EquipmentView> pageEquipment(QcQuery query) {
        List<EquipmentView> records = equipmentRepository.page(query).stream().map(EquipmentView::of).toList();
        return PageResult.of(records, equipmentRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentView createEquipment(EquipmentSaveCommand command) {
        if (equipmentRepository.findByEquipmentNo(command.getEquipmentNo()).isPresent()) {
            throw ErpErrorCode.exception(ErpErrorCode.EQUIPMENT_CODE_CONFLICT);
        }
        Equipment equipment = new Equipment();
        equipment.setEquipmentNo(command.getEquipmentNo().trim());
        applyEquipment(equipment, command);
        equipment.validateForCreate();
        equipmentRepository.insert(equipment);
        return EquipmentView.of(equipment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentView updateEquipment(String equipmentNo, EquipmentSaveCommand command) {
        Equipment equipment = requireEquipment(equipmentNo);
        applyEquipment(equipment, command);
        equipment.validateForCreate();
        equipmentRepository.update(equipment);
        return EquipmentView.of(equipment);
    }

    @Override
    public PageResult<MaintenancePlanView> pagePlans(QcQuery query) {
        List<MaintenancePlanView> records = maintenanceRepository.pagePlans(query).stream()
                .map(MaintenancePlanView::of).toList();
        return PageResult.of(records, maintenanceRepository.countPlans(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenancePlanView createPlan(MaintenancePlanSaveCommand command) {
        requireEquipment(command.getEquipmentNo());
        MaintenancePlan plan = new MaintenancePlan();
        plan.setPlanNo(maintenanceRepository.nextPlanNo(LocalDate.now()));
        applyPlan(plan, command);
        plan.validateForCreate();
        maintenanceRepository.insertPlan(plan);
        return MaintenancePlanView.of(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenancePlanView updatePlan(Long id, MaintenancePlanSaveCommand command) {
        MaintenancePlan plan = requirePlan(id);
        requireEquipment(command.getEquipmentNo());
        applyPlan(plan, command);
        plan.validateForCreate();
        maintenanceRepository.updatePlan(plan);
        return MaintenancePlanView.of(plan);
    }

    @Override
    public PageResult<MaintenanceOrderView> pageOrders(QcQuery query) {
        List<MaintenanceOrderView> records = maintenanceRepository.pageOrders(query).stream()
                .map(MaintenanceOrderView::of).toList();
        return PageResult.of(records, maintenanceRepository.countOrders(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceOrderView createOrder(MaintenanceOrderCreateCommand command) {
        requireEquipment(command.getEquipmentNo());
        MaintenancePlan plan = null;
        if (command.getPlanId() != null) {
            plan = requirePlan(command.getPlanId());
            if (!plan.getEquipmentNo().equals(command.getEquipmentNo())) {
                throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "保养计划与设备编码不匹配");
            }
            if (!plan.enabled()) {
                throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "保养计划已停用，不能生成保养工单");
            }
        }
        MaintenanceOrder order = new MaintenanceOrder();
        order.setOrderNo(maintenanceRepository.nextOrderNo(command.getPlanDate()));
        order.setEquipmentNo(command.getEquipmentNo());
        order.setPlanId(plan == null ? null : plan.getId());
        order.setMaintenanceType(command.getMaintenanceType());
        order.setStatus(QcStatus.MAINTENANCE_OPEN);
        order.setPlanDate(command.getPlanDate());
        order.setCostAmount(BigDecimal.ZERO);
        order.setRemark(command.getRemark());
        order.validateForCreate();
        maintenanceRepository.insertOrder(order);
        return MaintenanceOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceOrderView completeOrder(Long id, MaintenanceCompleteCommand command) {
        MaintenanceOrder order = maintenanceRepository.findOrderById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.MAINTENANCE_ORDER_NOT_FOUND));
        order.complete(command.getCompleteDate(), command.getCostAmount(), command.getResult());
        maintenanceRepository.updateOrder(order);
        if (order.getPlanId() != null) {
            MaintenancePlan plan = requirePlan(order.getPlanId());
            plan.advance(command.getCompleteDate());
            maintenanceRepository.updatePlan(plan);
        }
        return MaintenanceOrderView.of(order);
    }

    @Override
    public PageResult<OeeRecordView> pageOee(QcQuery query) {
        List<OeeRecordView> records = oeeRepository.page(query).stream().map(OeeRecordView::of).toList();
        return PageResult.of(records, oeeRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OeeRecordView recordOee(OeeRecordCommand command) {
        Equipment equipment = requireEquipment(command.getEquipmentNo());
        if (QcStatus.EQUIPMENT_RETIRED.equals(equipment.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "设备已退役，不能回传 OEE 数据");
        }
        OeeRecord record = new OeeRecord();
        oeeRepository.findByEquipmentAndDate(command.getEquipmentNo(), command.getRecordDate())
                .ifPresent(existing -> record.setId(existing.getId()));
        record.setEquipmentNo(command.getEquipmentNo());
        record.setRecordDate(command.getRecordDate());
        record.setPlannedMinutes(command.getPlannedMinutes());
        record.setRunMinutes(command.getRunMinutes());
        record.setDownMinutes(command.getDownMinutes());
        record.setIdealQty(command.getIdealQty());
        record.setOutputQty(command.getOutputQty());
        record.setGoodQty(command.getGoodQty());
        record.compute();
        if (record.getId() == null) {
            oeeRepository.insert(record);
        } else {
            oeeRepository.update(record);
        }
        return OeeRecordView.of(record);
    }

    /**
     * 填充设备可编辑字段。
     *
     * @param equipment 设备
     * @param command 保存命令
     */
    private void applyEquipment(Equipment equipment, EquipmentSaveCommand command) {
        equipment.setName(command.getName().trim());
        equipment.setEquipmentType(command.getEquipmentType());
        equipment.setLocation(command.getLocation());
        equipment.setStatus(command.getStatus());
        equipment.setPurchaseDate(command.getPurchaseDate());
        equipment.setRemark(command.getRemark());
    }

    /**
     * 填充保养计划可编辑字段。
     *
     * @param plan 保养计划
     * @param command 保存命令
     */
    private void applyPlan(MaintenancePlan plan, MaintenancePlanSaveCommand command) {
        plan.setEquipmentNo(command.getEquipmentNo());
        plan.setCycleDays(command.getCycleDays());
        plan.setLastDate(command.getLastDate());
        plan.setNextDate(command.getNextDate() != null ? command.getNextDate()
                : command.getLastDate() == null ? null : command.getLastDate().plusDays(command.getCycleDays()));
        plan.setStatus(command.getStatus() == null || command.getStatus().isBlank()
                ? QcStatus.ENABLED : command.getStatus());
        plan.setRemark(command.getRemark());
    }

    /**
     * 查询设备，不存在按 404 处理。
     *
     * @param equipmentNo 设备编码
     * @return 设备
     */
    private Equipment requireEquipment(String equipmentNo) {
        return equipmentRepository.findByEquipmentNo(equipmentNo)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.EQUIPMENT_NOT_FOUND,
                        "设备 " + equipmentNo + " 不存在或无权访问"));
    }

    /**
     * 查询保养计划，不存在按 404 处理。
     *
     * @param id 主键
     * @return 保养计划
     */
    private MaintenancePlan requirePlan(Long id) {
        return maintenanceRepository.findPlanById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.MAINTENANCE_PLAN_NOT_FOUND));
    }
}
