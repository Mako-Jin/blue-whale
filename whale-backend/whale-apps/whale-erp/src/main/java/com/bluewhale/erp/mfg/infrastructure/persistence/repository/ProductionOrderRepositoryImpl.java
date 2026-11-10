package com.bluewhale.erp.mfg.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.mfg.domain.model.MaterialAdjustment;
import com.bluewhale.erp.mfg.domain.model.MesReconcileQuery;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.model.ProductionMaterial;
import com.bluewhale.erp.mfg.domain.model.ProductionOperation;
import com.bluewhale.erp.mfg.domain.model.ProductionOrder;
import com.bluewhale.erp.mfg.domain.repository.ProductionOrderRepository;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.MesProgressEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.ProductionMaterialAdjustmentEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.ProductionMaterialEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.ProductionOperationEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.ProductionOrderEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.MesProgressMapper;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.ProductionMaterialAdjustmentMapper;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.ProductionMaterialMapper;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.ProductionOperationMapper;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.ProductionOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 生产订单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ProductionOrderRepositoryImpl implements ProductionOrderRepository {

    /** 订单号前缀 */
    private static final String DOC_PREFIX = "MO-";

    /** 用料调整单号前缀 */
    private static final String ADJ_PREFIX = "MA-";

    /** 单号日期格式 */
    private static final DateTimeFormatter DOC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductionOrderMapper productionOrderMapper;

    private final ProductionMaterialMapper productionMaterialMapper;

    private final ProductionOperationMapper productionOperationMapper;

    private final ProductionMaterialAdjustmentMapper productionMaterialAdjustmentMapper;

    private final MesProgressMapper mesProgressMapper;

    @Override
    public List<ProductionOrder> page(MfgQuery query) {
        Page<ProductionOrderEntity> page = productionOrderMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(MfgQuery query) {
        return productionOrderMapper.selectCount(filter(query));
    }

    @Override
    public Optional<ProductionOrder> findById(Long id) {
        ProductionOrderEntity entity = productionOrderMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        ProductionOrder order = toDomain(entity);
        order.setMaterials(productionMaterialMapper.selectList(new LambdaQueryWrapper<ProductionMaterialEntity>()
                        .eq(ProductionMaterialEntity::getOrderId, id)
                        .orderByAsc(ProductionMaterialEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        order.setOperations(operationsOf(id));
        return Optional.of(order);
    }

    @Override
    public Optional<ProductionOrder> findByOrderNo(String orderNo) {
        ProductionOrderEntity entity = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrderEntity>()
                .eq(ProductionOrderEntity::getOrderNo, orderNo)
                .last("limit 1"));
        if (entity == null) {
            return Optional.empty();
        }
        ProductionOrder order = toDomain(entity);
        order.setMaterials(productionMaterialMapper.selectList(new LambdaQueryWrapper<ProductionMaterialEntity>()
                        .eq(ProductionMaterialEntity::getOrderId, entity.getId())
                        .orderByAsc(ProductionMaterialEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        order.setOperations(operationsOf(entity.getId()));
        return Optional.of(order);
    }

    @Override
    public List<ProductionOrder> pageForMes(MesReconcileQuery query) {
        Page<ProductionOrderEntity> page = productionOrderMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filterForMes(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countForMes(MesReconcileQuery query) {
        return productionOrderMapper.selectCount(filterForMes(query));
    }

    @Override
    public void updateMesSync(ProductionOrder order) {
        ProductionOrderEntity entity = new ProductionOrderEntity();
        entity.setId(order.getId());
        entity.setMesSyncStatus(order.getMesSyncStatus());
        entity.setMesWorkOrderCode(order.getMesWorkOrderCode());
        entity.setMesSyncedAt(order.getMesSyncedAt());
        entity.setMesLastError(order.getMesLastError());
        productionOrderMapper.updateById(entity);
    }

    /**
     * 构建 MES 对账查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<ProductionOrderEntity> filterForMes(MesReconcileQuery query) {
        LambdaQueryWrapper<ProductionOrderEntity> wrapper = filter(query);
        wrapper.in(ProductionOrderEntity::getStatus,
                MfgStatus.RELEASED, MfgStatus.IN_PROGRESS, MfgStatus.COMPLETED, MfgStatus.CLOSED);
        if (StringUtils.hasText(query.getMesSyncStatus())) {
            wrapper.eq(ProductionOrderEntity::getMesSyncStatus, query.getMesSyncStatus());
        }
        if (StringUtils.hasText(query.getMesStatus())) {
            List<String> orderNos = mesProgressMapper.selectList(new LambdaQueryWrapper<MesProgressEntity>()
                            .eq(MesProgressEntity::getMesStatus, query.getMesStatus()))
                    .stream().map(MesProgressEntity::getErpOrderNo).toList();
            if (orderNos.isEmpty()) {
                wrapper.eq(ProductionOrderEntity::getOrderNo, "__MES_NO_MATCH__");
            } else {
                wrapper.in(ProductionOrderEntity::getOrderNo, orderNos);
            }
        }
        return wrapper;
    }

    @Override
    public List<ProductionOrder> listActiveForCrp(LocalDate fromDate, LocalDate toDate) {
        return productionOrderMapper.selectList(new LambdaQueryWrapper<ProductionOrderEntity>()
                        .in(ProductionOrderEntity::getStatus, MfgStatus.RELEASED, MfgStatus.IN_PROGRESS)
                        .le(ProductionOrderEntity::getPlanStartDate, toDate)
                        .orderByAsc(ProductionOrderEntity::getPlanStartDate)
                        .orderByAsc(ProductionOrderEntity::getOrderNo))
                .stream()
                .map(entity -> {
                    ProductionOrder order = toDomain(entity);
                    order.setOperations(operationsOf(entity.getId()));
                    return order;
                })
                .toList();
    }

    @Override
    public String nextOrderNo(LocalDate planStartDate) {
        long sequence = productionOrderMapper.selectCount(new LambdaQueryWrapper<ProductionOrderEntity>()
                .eq(ProductionOrderEntity::getPlanStartDate, planStartDate)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, planStartDate.format(DOC_DATE_FORMATTER), sequence);
    }

    @Override
    public void insert(ProductionOrder order) {
        ProductionOrderEntity entity = new ProductionOrderEntity();
        fill(entity, order);
        productionOrderMapper.insert(entity);
        order.setId(entity.getId());
        for (ProductionMaterial material : order.getMaterials()) {
            ProductionMaterialEntity materialEntity = new ProductionMaterialEntity();
            materialEntity.setOrderId(entity.getId());
            fillMaterial(materialEntity, material);
            productionMaterialMapper.insert(materialEntity);
        }
        insertOperations(order);
    }

    @Override
    public void updateRouting(ProductionOrder order) {
        ProductionOrderEntity entity = new ProductionOrderEntity();
        entity.setId(order.getId());
        entity.setRoutingNo(order.getRoutingNo());
        entity.setRoutingVersion(order.getRoutingVersion());
        entity.setRoutingStatus(order.getRoutingStatus());
        productionOrderMapper.updateById(entity);
        productionOperationMapper.delete(new LambdaQueryWrapper<ProductionOperationEntity>()
                .eq(ProductionOperationEntity::getOrderId, order.getId()));
        order.getOperations().forEach(operation ->
                productionOperationMapper.insert(toEntity(order.getId(), operation)));
    }

    @Override
    public void update(ProductionOrder order) {
        ProductionOrderEntity entity = new ProductionOrderEntity();
        entity.setId(order.getId());
        entity.setStatus(order.getStatus());
        entity.setCompletedQuantity(order.getCompletedQuantity());
        entity.setCompletedCost(order.getCompletedCost());
        entity.setTotalMaterialCost(order.getTotalMaterialCost());
        entity.setCompleteDate(order.getCompleteDate());
        productionOrderMapper.updateById(entity);
        for (ProductionMaterial material : order.getMaterials()) {
            ProductionMaterialEntity materialEntity = productionMaterialMapper.selectOne(
                    new LambdaQueryWrapper<ProductionMaterialEntity>()
                            .eq(ProductionMaterialEntity::getOrderId, order.getId())
                            .eq(ProductionMaterialEntity::getLineNo, material.getLineNo())
                            .last("limit 1"));
            if (materialEntity != null) {
                materialEntity.setIssuedQuantity(material.getIssuedQuantity());
                materialEntity.setIssuedCost(material.getIssuedCost());
                productionMaterialMapper.updateById(materialEntity);
            }
        }
        updateOperations(order);
    }

    @Override
    public String nextAdjustmentNo(LocalDate occurredDate) {
        String prefix = ADJ_PREFIX + occurredDate.format(DOC_DATE_FORMATTER) + "-";
        long sequence = productionMaterialAdjustmentMapper.selectCount(
                new LambdaQueryWrapper<ProductionMaterialAdjustmentEntity>()
                        .likeRight(ProductionMaterialAdjustmentEntity::getDocNo, prefix)) + 1;
        return String.format("%s%04d", prefix, sequence);
    }

    @Override
    public void insertAdjustment(MaterialAdjustment adjustment) {
        ProductionMaterialAdjustmentEntity entity = new ProductionMaterialAdjustmentEntity();
        entity.setOrderId(adjustment.getOrderId());
        entity.setDocNo(adjustment.getDocNo());
        entity.setAdjType(adjustment.getAdjType());
        entity.setLineNo(adjustment.getLineNo());
        entity.setItemId(adjustment.getItemId());
        entity.setItemCode(adjustment.getItemCode());
        entity.setItemName(adjustment.getItemName());
        entity.setQuantity(adjustment.getQuantity());
        entity.setAmount(adjustment.getAmount());
        entity.setReason(adjustment.getReason());
        entity.setOperatorId(adjustment.getOperatorId());
        entity.setOccurredAt(adjustment.getOccurredAt());
        productionMaterialAdjustmentMapper.insert(entity);
    }

    /**
     * 更新订单工序累计报工数量与状态。
     *
     * @param order 生产订单
     */
    private void updateOperations(ProductionOrder order) {
        if (order.getOperations() == null) {
            return;
        }
        for (ProductionOperation operation : order.getOperations()) {
            ProductionOperationEntity entity = productionOperationMapper.selectOne(
                    new LambdaQueryWrapper<ProductionOperationEntity>()
                            .eq(ProductionOperationEntity::getOrderId, order.getId())
                            .eq(ProductionOperationEntity::getStepNo, operation.getStepNo())
                            .last("limit 1"));
            if (entity == null) {
                continue;
            }
            entity.setQualifiedQuantity(operation.getQualifiedQuantity());
            entity.setScrappedQuantity(operation.getScrappedQuantity());
            entity.setStatus(operation.getStatus());
            entity.setFirstReportAt(operation.getFirstReportAt());
            entity.setLastReportAt(operation.getLastReportAt());
            productionOperationMapper.updateById(entity);
        }
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<ProductionOrderEntity> filter(MfgQuery query) {
        LambdaQueryWrapper<ProductionOrderEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(ProductionOrderEntity::getOrderNo, query.getKeyword())
                    .or().like(ProductionOrderEntity::getItemCode, query.getKeyword())
                    .or().like(ProductionOrderEntity::getItemName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ProductionOrderEntity::getStatus, query.getStatus());
        }
        if (query.getItemId() != null) {
            wrapper.eq(ProductionOrderEntity::getItemId, query.getItemId());
        }
        if (query.getWarehouseId() != null) {
            wrapper.and(w -> w.eq(ProductionOrderEntity::getIssueWarehouseId, query.getWarehouseId())
                    .or().eq(ProductionOrderEntity::getTargetWarehouseId, query.getWarehouseId()));
        }
        if (query.getFromDate() != null) {
            wrapper.ge(ProductionOrderEntity::getPlanStartDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(ProductionOrderEntity::getPlanStartDate, query.getToDate());
        }
        return wrapper.orderByDesc(ProductionOrderEntity::getPlanStartDate)
                .orderByDesc(ProductionOrderEntity::getOrderNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ProductionOrder toDomain(ProductionOrderEntity entity) {
        ProductionOrder order = new ProductionOrder();
        order.setId(entity.getId());
        order.setOrderNo(entity.getOrderNo());
        order.setItemId(entity.getItemId());
        order.setItemCode(entity.getItemCode());
        order.setItemName(entity.getItemName());
        order.setPlannedQuantity(entity.getPlannedQuantity());
        order.setCompletedQuantity(entity.getCompletedQuantity());
        order.setCompletedCost(entity.getCompletedCost());
        order.setTotalMaterialCost(entity.getTotalMaterialCost());
        order.setIssueWarehouseId(entity.getIssueWarehouseId());
        order.setIssueWarehouseName(entity.getIssueWarehouseName());
        order.setTargetWarehouseId(entity.getTargetWarehouseId());
        order.setTargetWarehouseName(entity.getTargetWarehouseName());
        order.setPlanStartDate(entity.getPlanStartDate());
        order.setPlanEndDate(entity.getPlanEndDate());
        order.setCompleteDate(entity.getCompleteDate());
        order.setStatus(entity.getStatus());
        order.setRemark(entity.getRemark());
        order.setRoutingNo(entity.getRoutingNo());
        order.setRoutingVersion(entity.getRoutingVersion());
        order.setRoutingStatus(entity.getRoutingStatus());
        order.setMesSyncStatus(entity.getMesSyncStatus());
        order.setMesWorkOrderCode(entity.getMesWorkOrderCode());
        order.setMesSyncedAt(entity.getMesSyncedAt());
        order.setMesLastError(entity.getMesLastError());
        return order;
    }

    /**
     * 读取订单工序。
     *
     * @param orderId 订单 ID
     * @return 工序列表
     */
    private List<ProductionOperation> operationsOf(Long orderId) {
        return productionOperationMapper.selectList(new LambdaQueryWrapper<ProductionOperationEntity>()
                        .eq(ProductionOperationEntity::getOrderId, orderId)
                        .orderByAsc(ProductionOperationEntity::getStepNo))
                .stream().map(this::toDomainOperation).toList();
    }

    /**
     * 工序实体转领域模型。
     *
     * @param entity 实体
     * @return 工序
     */
    private ProductionOperation toDomainOperation(ProductionOperationEntity entity) {
        ProductionOperation operation = new ProductionOperation();
        operation.setStepNo(entity.getStepNo());
        operation.setName(entity.getName());
        operation.setWorkCenterCode(entity.getWorkCenterCode());
        operation.setStandardHours(entity.getStandardHours());
        operation.setSetupHours(entity.getSetupHours());
        operation.setPlannedQuantity(entity.getPlannedQuantity());
        operation.setQualifiedQuantity(entity.getQualifiedQuantity());
        operation.setScrappedQuantity(entity.getScrappedQuantity());
        operation.setStatus(entity.getStatus());
        operation.setFirstReportAt(entity.getFirstReportAt());
        operation.setLastReportAt(entity.getLastReportAt());
        return operation;
    }

    /**
     * 工序领域模型转实体。
     *
     * @param orderId   订单 ID
     * @param operation 工序
     * @return 实体
     */
    private ProductionOperationEntity toEntity(Long orderId, ProductionOperation operation) {
        ProductionOperationEntity entity = new ProductionOperationEntity();
        entity.setOrderId(orderId);
        entity.setStepNo(operation.getStepNo());
        entity.setName(operation.getName());
        entity.setWorkCenterCode(operation.getWorkCenterCode());
        entity.setStandardHours(operation.getStandardHours());
        entity.setSetupHours(operation.getSetupHours());
        entity.setPlannedQuantity(operation.getPlannedQuantity());
        entity.setQualifiedQuantity(operation.getQualifiedQuantity());
        entity.setScrappedQuantity(operation.getScrappedQuantity());
        entity.setStatus(operation.getStatus());
        entity.setFirstReportAt(operation.getFirstReportAt());
        entity.setLastReportAt(operation.getLastReportAt());
        return entity;
    }

    /**
     * 写入订单工序。
     *
     * @param order 订单
     */
    private void insertOperations(ProductionOrder order) {
        if (order.getOperations() == null) {
            return;
        }
        order.getOperations().forEach(operation ->
                productionOperationMapper.insert(toEntity(order.getId(), operation)));
    }

    /**
     * 用料实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ProductionMaterial toDomain(ProductionMaterialEntity entity) {
        ProductionMaterial material = new ProductionMaterial();
        material.setLineNo(entity.getLineNo());
        material.setItemId(entity.getItemId());
        material.setItemCode(entity.getItemCode());
        material.setItemName(entity.getItemName());
        material.setUom(entity.getUom());
        material.setRequiredQuantity(entity.getRequiredQuantity());
        material.setIssuedQuantity(entity.getIssuedQuantity());
        material.setIssuedCost(entity.getIssuedCost());
        material.setBackflush(entity.getBackflush() != null && entity.getBackflush() == 1);
        return material;
    }

    /**
     * 领域模型写入单头实体。
     *
     * @param entity 实体
     * @param order 领域模型
     */
    private void fill(ProductionOrderEntity entity, ProductionOrder order) {
        entity.setOrderNo(order.getOrderNo());
        entity.setItemId(order.getItemId());
        entity.setItemCode(order.getItemCode());
        entity.setItemName(order.getItemName());
        entity.setPlannedQuantity(order.getPlannedQuantity());
        entity.setCompletedQuantity(order.getCompletedQuantity());
        entity.setCompletedCost(order.getCompletedCost());
        entity.setTotalMaterialCost(order.getTotalMaterialCost());
        entity.setIssueWarehouseId(order.getIssueWarehouseId());
        entity.setIssueWarehouseName(order.getIssueWarehouseName());
        entity.setTargetWarehouseId(order.getTargetWarehouseId());
        entity.setTargetWarehouseName(order.getTargetWarehouseName());
        entity.setPlanStartDate(order.getPlanStartDate());
        entity.setPlanEndDate(order.getPlanEndDate());
        entity.setCompleteDate(order.getCompleteDate());
        entity.setStatus(order.getStatus());
        entity.setRemark(order.getRemark());
        entity.setRoutingNo(order.getRoutingNo());
        entity.setRoutingVersion(order.getRoutingVersion());
        entity.setRoutingStatus(order.getRoutingStatus());
        entity.setMesSyncStatus(order.getMesSyncStatus());
        entity.setMesWorkOrderCode(order.getMesWorkOrderCode());
        entity.setMesSyncedAt(order.getMesSyncedAt());
        entity.setMesLastError(order.getMesLastError());
    }

    /**
     * 领域模型写入用料实体。
     *
     * @param entity 实体
     * @param material 领域模型
     */
    private void fillMaterial(ProductionMaterialEntity entity, ProductionMaterial material) {
        entity.setLineNo(material.getLineNo());
        entity.setItemId(material.getItemId());
        entity.setItemCode(material.getItemCode());
        entity.setItemName(material.getItemName());
        entity.setUom(material.getUom());
        entity.setRequiredQuantity(material.getRequiredQuantity());
        entity.setIssuedQuantity(material.getIssuedQuantity());
        entity.setIssuedCost(material.getIssuedCost() == null ? BigDecimal.ZERO : material.getIssuedCost());
        entity.setBackflush(Boolean.TRUE.equals(material.getBackflush()) ? 1 : 0);
    }
}
