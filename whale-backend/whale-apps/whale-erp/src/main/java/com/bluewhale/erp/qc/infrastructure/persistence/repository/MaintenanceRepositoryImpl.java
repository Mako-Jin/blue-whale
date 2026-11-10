package com.bluewhale.erp.qc.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.qc.domain.model.MaintenanceOrder;
import com.bluewhale.erp.qc.domain.model.MaintenancePlan;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import com.bluewhale.erp.qc.domain.repository.MaintenanceRepository;
import com.bluewhale.erp.qc.infrastructure.persistence.entity.MaintenanceOrderEntity;
import com.bluewhale.erp.qc.infrastructure.persistence.entity.MaintenancePlanEntity;
import com.bluewhale.erp.qc.infrastructure.persistence.mapper.MaintenanceOrderMapper;
import com.bluewhale.erp.qc.infrastructure.persistence.mapper.MaintenancePlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 设备保养计划与保养工单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MaintenanceRepositoryImpl implements MaintenanceRepository {

    /** 保养计划号前缀 */
    private static final String PLAN_PREFIX = "MT-";

    /** 保养工单号前缀 */
    private static final String ORDER_PREFIX = "MTW-";

    /** 单号期间格式 */
    private static final DateTimeFormatter DOC_PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final MaintenancePlanMapper maintenancePlanMapper;

    private final MaintenanceOrderMapper maintenanceOrderMapper;

    @Override
    public List<MaintenancePlan> pagePlans(QcQuery query) {
        Page<MaintenancePlanEntity> page = maintenancePlanMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), planFilter(query));
        return page.getRecords().stream().map(this::toPlan).toList();
    }

    @Override
    public long countPlans(QcQuery query) {
        return maintenancePlanMapper.selectCount(planFilter(query));
    }

    @Override
    public Optional<MaintenancePlan> findPlanById(Long id) {
        MaintenancePlanEntity entity = maintenancePlanMapper.selectById(id);
        return entity == null ? Optional.empty() : Optional.of(toPlan(entity));
    }

    @Override
    public String nextPlanNo(LocalDate date) {
        String period = date.format(DOC_PERIOD_FORMATTER);
        long sequence = maintenancePlanMapper.selectCount(new LambdaQueryWrapper<MaintenancePlanEntity>()
                .likeRight(MaintenancePlanEntity::getPlanNo, PLAN_PREFIX + period)) + 1;
        return String.format("%s%s-%04d", PLAN_PREFIX, period, sequence);
    }

    @Override
    public void insertPlan(MaintenancePlan plan) {
        MaintenancePlanEntity entity = new MaintenancePlanEntity();
        fillPlan(entity, plan);
        maintenancePlanMapper.insert(entity);
        plan.setId(entity.getId());
    }

    @Override
    public void updatePlan(MaintenancePlan plan) {
        MaintenancePlanEntity entity = new MaintenancePlanEntity();
        entity.setId(plan.getId());
        entity.setEquipmentNo(plan.getEquipmentNo());
        entity.setCycleDays(plan.getCycleDays());
        entity.setLastDate(plan.getLastDate());
        entity.setNextDate(plan.getNextDate());
        entity.setStatus(plan.getStatus());
        entity.setRemark(plan.getRemark());
        maintenancePlanMapper.updateById(entity);
    }

    @Override
    public List<MaintenanceOrder> pageOrders(QcQuery query) {
        Page<MaintenanceOrderEntity> page = maintenanceOrderMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), orderFilter(query));
        return page.getRecords().stream().map(this::toOrder).toList();
    }

    @Override
    public long countOrders(QcQuery query) {
        return maintenanceOrderMapper.selectCount(orderFilter(query));
    }

    @Override
    public Optional<MaintenanceOrder> findOrderById(Long id) {
        MaintenanceOrderEntity entity = maintenanceOrderMapper.selectById(id);
        return entity == null ? Optional.empty() : Optional.of(toOrder(entity));
    }

    @Override
    public String nextOrderNo(LocalDate date) {
        String period = date.format(DOC_PERIOD_FORMATTER);
        long sequence = maintenanceOrderMapper.selectCount(new LambdaQueryWrapper<MaintenanceOrderEntity>()
                .likeRight(MaintenanceOrderEntity::getOrderNo, ORDER_PREFIX + period)) + 1;
        return String.format("%s%s-%04d", ORDER_PREFIX, period, sequence);
    }

    @Override
    public void insertOrder(MaintenanceOrder order) {
        MaintenanceOrderEntity entity = new MaintenanceOrderEntity();
        fillOrder(entity, order);
        maintenanceOrderMapper.insert(entity);
        order.setId(entity.getId());
    }

    @Override
    public void updateOrder(MaintenanceOrder order) {
        MaintenanceOrderEntity entity = new MaintenanceOrderEntity();
        entity.setId(order.getId());
        entity.setStatus(order.getStatus());
        entity.setCompleteDate(order.getCompleteDate());
        entity.setCostAmount(order.getCostAmount());
        entity.setResult(order.getResult());
        entity.setRemark(order.getRemark());
        maintenanceOrderMapper.updateById(entity);
    }

    /**
     * 构建保养计划查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<MaintenancePlanEntity> planFilter(QcQuery query) {
        LambdaQueryWrapper<MaintenancePlanEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(MaintenancePlanEntity::getPlanNo, query.getKeyword())
                    .or().like(MaintenancePlanEntity::getEquipmentNo, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getEquipmentNo())) {
            wrapper.eq(MaintenancePlanEntity::getEquipmentNo, query.getEquipmentNo());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(MaintenancePlanEntity::getStatus, query.getStatus());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(MaintenancePlanEntity::getNextDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(MaintenancePlanEntity::getNextDate, query.getToDate());
        }
        return wrapper.orderByAsc(MaintenancePlanEntity::getNextDate);
    }

    /**
     * 构建保养工单查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<MaintenanceOrderEntity> orderFilter(QcQuery query) {
        LambdaQueryWrapper<MaintenanceOrderEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(MaintenanceOrderEntity::getOrderNo, query.getKeyword())
                    .or().like(MaintenanceOrderEntity::getEquipmentNo, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getEquipmentNo())) {
            wrapper.eq(MaintenanceOrderEntity::getEquipmentNo, query.getEquipmentNo());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(MaintenanceOrderEntity::getStatus, query.getStatus());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(MaintenanceOrderEntity::getPlanDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(MaintenanceOrderEntity::getPlanDate, query.getToDate());
        }
        return wrapper.orderByDesc(MaintenanceOrderEntity::getPlanDate)
                .orderByDesc(MaintenanceOrderEntity::getOrderNo);
    }

    /**
     * 保养计划实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MaintenancePlan toPlan(MaintenancePlanEntity entity) {
        MaintenancePlan plan = new MaintenancePlan();
        plan.setId(entity.getId());
        plan.setPlanNo(entity.getPlanNo());
        plan.setEquipmentNo(entity.getEquipmentNo());
        plan.setCycleDays(entity.getCycleDays());
        plan.setLastDate(entity.getLastDate());
        plan.setNextDate(entity.getNextDate());
        plan.setStatus(entity.getStatus());
        plan.setRemark(entity.getRemark());
        return plan;
    }

    /**
     * 保养工单实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MaintenanceOrder toOrder(MaintenanceOrderEntity entity) {
        MaintenanceOrder order = new MaintenanceOrder();
        order.setId(entity.getId());
        order.setOrderNo(entity.getOrderNo());
        order.setEquipmentNo(entity.getEquipmentNo());
        order.setPlanId(entity.getPlanId());
        order.setMaintenanceType(entity.getMaintenanceType());
        order.setStatus(entity.getStatus());
        order.setPlanDate(entity.getPlanDate());
        order.setCompleteDate(entity.getCompleteDate());
        order.setCostAmount(entity.getCostAmount());
        order.setResult(entity.getResult());
        order.setRemark(entity.getRemark());
        return order;
    }

    /**
     * 领域模型写入保养计划实体。
     *
     * @param entity 实体
     * @param plan 保养计划
     */
    private void fillPlan(MaintenancePlanEntity entity, MaintenancePlan plan) {
        entity.setPlanNo(plan.getPlanNo());
        entity.setEquipmentNo(plan.getEquipmentNo());
        entity.setCycleDays(plan.getCycleDays());
        entity.setLastDate(plan.getLastDate());
        entity.setNextDate(plan.getNextDate());
        entity.setStatus(plan.getStatus());
        entity.setRemark(plan.getRemark());
    }

    /**
     * 领域模型写入保养工单实体。
     *
     * @param entity 实体
     * @param order 保养工单
     */
    private void fillOrder(MaintenanceOrderEntity entity, MaintenanceOrder order) {
        entity.setOrderNo(order.getOrderNo());
        entity.setEquipmentNo(order.getEquipmentNo());
        entity.setPlanId(order.getPlanId());
        entity.setMaintenanceType(order.getMaintenanceType());
        entity.setStatus(order.getStatus());
        entity.setPlanDate(order.getPlanDate());
        entity.setCompleteDate(order.getCompleteDate());
        entity.setCostAmount(order.getCostAmount());
        entity.setResult(order.getResult());
        entity.setRemark(order.getRemark());
    }
}
