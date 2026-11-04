package com.bluewhale.erp.mfg.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.ProductionMaterial;
import com.bluewhale.erp.mfg.domain.model.ProductionOrder;
import com.bluewhale.erp.mfg.domain.repository.ProductionOrderRepository;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.ProductionMaterialEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.ProductionOrderEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.ProductionMaterialMapper;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.ProductionOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

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

    /** 单号日期格式 */
    private static final DateTimeFormatter DOC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductionOrderMapper productionOrderMapper;

    private final ProductionMaterialMapper productionMaterialMapper;

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
        return Optional.of(order);
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
                productionMaterialMapper.updateById(materialEntity);
            }
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
        return order;
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
    }
}
