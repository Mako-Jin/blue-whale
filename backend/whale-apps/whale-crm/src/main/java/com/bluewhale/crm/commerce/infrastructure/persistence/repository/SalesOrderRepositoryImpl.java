package com.bluewhale.crm.commerce.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.commerce.domain.model.SalesOrder;
import com.bluewhale.crm.commerce.domain.model.SalesOrderLine;
import com.bluewhale.crm.commerce.domain.model.SalesOrderQuery;
import com.bluewhale.crm.commerce.domain.model.SalesOrderStatus;
import com.bluewhale.crm.commerce.domain.repository.SalesOrderRepository;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.SalesOrderEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.SalesOrderLineEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.SalesOrderLineMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商务订单仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SalesOrderRepositoryImpl implements SalesOrderRepository {

    private final SalesOrderMapper salesOrderMapper;

    private final SalesOrderLineMapper salesOrderLineMapper;

    @Override
    public List<SalesOrder> page(SalesOrderQuery query) {
        Page<SalesOrderEntity> page = new Page<>(query.getCurrent(), query.getSize());
        return salesOrderMapper.selectPage(page, pageWrapper(query))
                .getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(SalesOrderQuery query) {
        return salesOrderMapper.selectCount(pageWrapper(query));
    }

    @Override
    public SalesOrder findById(Long id) {
        SalesOrderEntity entity = salesOrderMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public List<SalesOrderLine> findLines(Long orderId) {
        return salesOrderLineMapper.selectList(new LambdaQueryWrapper<SalesOrderLineEntity>()
                        .eq(SalesOrderLineEntity::getOrderId, orderId)
                        .orderByAsc(SalesOrderLineEntity::getLineNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public SalesOrder findByApprovalInstanceId(Long instanceId) {
        SalesOrderEntity entity = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getApprovalInstanceId, instanceId)
                .last("limit 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public SalesOrder findBySource(String sourceType, Long sourceId) {
        if (!StringUtils.hasText(sourceType) || sourceId == null) {
            return null;
        }
        SalesOrderEntity entity = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getSourceType, sourceType)
                .eq(SalesOrderEntity::getSourceId, sourceId)
                .last("limit 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(SalesOrder order) {
        SalesOrderEntity entity = toEntity(order);
        salesOrderMapper.insert(entity);
        order.setId(entity.getId());
        insertLines(order);
    }

    @Override
    public void replaceLines(SalesOrder order) {
        salesOrderLineMapper.delete(new LambdaQueryWrapper<SalesOrderLineEntity>()
                .eq(SalesOrderLineEntity::getOrderId, order.getId()));
        insertLines(order);
    }

    /**
     * 插入订单行。
     *
     * @param order 订单
     */
    private void insertLines(SalesOrder order) {
        for (SalesOrderLine line : order.getLines()) {
            SalesOrderLineEntity lineEntity = toEntity(line);
            lineEntity.setOrderId(order.getId());
            salesOrderLineMapper.insert(lineEntity);
            line.setId(lineEntity.getId());
        }
    }

    @Override
    public boolean update(SalesOrder order) {
        return salesOrderMapper.updateById(toEntity(order)) > 0;
    }

    @Override
    public boolean applyApprovalResult(Long orderId, boolean approved, LocalDateTime at) {
        String target = approved ? SalesOrderStatus.APPROVED.name() : SalesOrderStatus.REJECTED.name();
        LambdaUpdateWrapper<SalesOrderEntity> wrapper = new LambdaUpdateWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getId, orderId)
                .eq(SalesOrderEntity::getStatus, SalesOrderStatus.PENDING_APPROVAL.name())
                .set(SalesOrderEntity::getStatus, target);
        if (approved) {
            wrapper.set(SalesOrderEntity::getApprovedAt, at);
        }
        return salesOrderMapper.update(null, wrapper) > 0;
    }

    @Override
    public List<SalesOrder> findByStatuses(List<String> statuses, int limit) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrderEntity>()
                        .in(SalesOrderEntity::getStatus, statuses)
                        .orderByAsc(SalesOrderEntity::getUpdateTime)
                        .last("limit " + Math.max(1, limit)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long maxSequence(String prefix) {
        return salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrderEntity>()
                .likeRight(SalesOrderEntity::getOrderNo, prefix));
    }

    /**
     * 构造分页查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<SalesOrderEntity> pageWrapper(SalesOrderQuery query) {
        LambdaQueryWrapper<SalesOrderEntity> wrapper = new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(StringUtils.hasText(query.getStatus()), SalesOrderEntity::getStatus, query.getStatus())
                .eq(query.getCustomerId() != null, SalesOrderEntity::getCustomerId, query.getCustomerId())
                .eq(query.getOwnerId() != null, SalesOrderEntity::getOwnerId, query.getOwnerId())
                .orderByDesc(SalesOrderEntity::getCreateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(SalesOrderEntity::getOrderNo, keyword)
                    .or().like(SalesOrderEntity::getTitle, keyword));
        }
        return wrapper;
    }

    /**
     * 实体转订单聚合（不含行）。
     *
     * @param entity 实体
     * @return 订单
     */
    private SalesOrder toDomain(SalesOrderEntity entity) {
        SalesOrder order = new SalesOrder();
        order.setId(entity.getId());
        order.setTenantId(entity.getTenantId());
        order.setOrderNo(entity.getOrderNo());
        order.setSourceType(entity.getSourceType());
        order.setSourceId(entity.getSourceId());
        order.setSourceOrderNo(entity.getSourceOrderNo());
        order.setCustomerId(entity.getCustomerId());
        order.setCustomerCode(entity.getCustomerCode());
        order.setTitle(entity.getTitle());
        order.setCurrency(entity.getCurrency());
        order.setTotalAmount(entity.getTotalAmount());
        order.setStatus(SalesOrderStatus.from(entity.getStatus()));
        order.setApprovalInstanceId(entity.getApprovalInstanceId());
        order.setSubmittedAt(entity.getSubmittedAt());
        order.setApprovedAt(entity.getApprovedAt());
        order.setErpOrderNo(entity.getErpOrderNo());
        order.setErpOrderId(entity.getErpOrderId());
        order.setErpStatus(entity.getErpStatus());
        order.setPushedAt(entity.getPushedAt());
        order.setPushAttempts(entity.getPushAttempts());
        order.setLastPushError(entity.getLastPushError());
        order.setOwnerId(entity.getOwnerId());
        order.setDeptId(entity.getDeptId());
        order.setRemark(entity.getRemark());
        order.setLockVersion(entity.getLockVersion());
        return order;
    }

    /**
     * 订单聚合转实体。
     *
     * @param order 订单
     * @return 实体
     */
    private SalesOrderEntity toEntity(SalesOrder order) {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(order.getId());
        entity.setTenantId(order.getTenantId());
        entity.setOrderNo(order.getOrderNo());
        entity.setSourceType(order.getSourceType());
        entity.setSourceId(order.getSourceId());
        entity.setSourceOrderNo(order.getSourceOrderNo());
        entity.setCustomerId(order.getCustomerId());
        entity.setCustomerCode(order.getCustomerCode());
        entity.setTitle(order.getTitle());
        entity.setCurrency(order.getCurrency());
        entity.setTotalAmount(order.getTotalAmount());
        entity.setStatus(order.getStatus() == null ? null : order.getStatus().name());
        entity.setApprovalInstanceId(order.getApprovalInstanceId());
        entity.setSubmittedAt(order.getSubmittedAt());
        entity.setApprovedAt(order.getApprovedAt());
        entity.setErpOrderNo(order.getErpOrderNo());
        entity.setErpOrderId(order.getErpOrderId());
        entity.setErpStatus(order.getErpStatus());
        entity.setPushedAt(order.getPushedAt());
        entity.setPushAttempts(order.getPushAttempts());
        entity.setLastPushError(order.getLastPushError());
        entity.setOwnerId(order.getOwnerId());
        entity.setDeptId(order.getDeptId());
        entity.setRemark(order.getRemark());
        entity.setLockVersion(order.getLockVersion());
        return entity;
    }

    /**
     * 行实体转领域对象。
     *
     * @param entity 行实体
     * @return 订单行
     */
    private SalesOrderLine toDomain(SalesOrderLineEntity entity) {
        SalesOrderLine line = new SalesOrderLine();
        line.setId(entity.getId());
        line.setOrderId(entity.getOrderId());
        line.setLineNo(entity.getLineNo());
        line.setItemCode(entity.getItemCode());
        line.setItemName(entity.getItemName());
        line.setSpecification(entity.getSpecification());
        line.setUom(entity.getUom());
        line.setQuantity(entity.getQuantity());
        line.setUnitPrice(entity.getUnitPrice());
        line.setAmount(entity.getAmount());
        line.setRemark(entity.getRemark());
        return line;
    }

    /**
     * 领域行转实体。
     *
     * @param line 订单行
     * @return 行实体
     */
    private SalesOrderLineEntity toEntity(SalesOrderLine line) {
        SalesOrderLineEntity entity = new SalesOrderLineEntity();
        entity.setId(line.getId());
        entity.setOrderId(line.getOrderId());
        entity.setLineNo(line.getLineNo());
        entity.setItemCode(line.getItemCode());
        entity.setItemName(line.getItemName());
        entity.setSpecification(line.getSpecification());
        entity.setUom(line.getUom());
        entity.setQuantity(line.getQuantity());
        entity.setUnitPrice(line.getUnitPrice());
        entity.setAmount(line.getAmount());
        entity.setRemark(line.getRemark());
        return entity;
    }
}
