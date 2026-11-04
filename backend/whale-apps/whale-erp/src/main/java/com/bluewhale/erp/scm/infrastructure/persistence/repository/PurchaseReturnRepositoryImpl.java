package com.bluewhale.erp.scm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.scm.domain.model.PurchaseReturn;
import com.bluewhale.erp.scm.domain.model.PurchaseReturnLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.repository.PurchaseReturnRepository;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.PurchaseReturnEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.PurchaseReturnLineEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.PurchaseReturnLineMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.PurchaseReturnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 采购退货单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PurchaseReturnRepositoryImpl implements PurchaseReturnRepository {

    /** 退货单号前缀 */
    private static final String DOC_PREFIX = "PR-";

    /** 单号日期格式 */
    private static final DateTimeFormatter DOC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PurchaseReturnMapper returnMapper;

    private final PurchaseReturnLineMapper returnLineMapper;

    @Override
    public List<PurchaseReturn> page(ScmQuery query) {
        Page<PurchaseReturnEntity> page = returnMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ScmQuery query) {
        return returnMapper.selectCount(filter(query));
    }

    @Override
    public Optional<PurchaseReturn> findById(Long id) {
        PurchaseReturnEntity entity = returnMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        PurchaseReturn purchaseReturn = toDomain(entity);
        purchaseReturn.setLines(returnLineMapper.selectList(new LambdaQueryWrapper<PurchaseReturnLineEntity>()
                        .eq(PurchaseReturnLineEntity::getReturnId, id)
                        .orderByAsc(PurchaseReturnLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(purchaseReturn);
    }

    @Override
    public String nextDocNo(LocalDate docDate) {
        long sequence = returnMapper.selectCount(new LambdaQueryWrapper<PurchaseReturnEntity>()
                .eq(PurchaseReturnEntity::getDocDate, docDate)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, docDate.format(DOC_DATE_FORMATTER), sequence);
    }

    @Override
    public void insert(PurchaseReturn purchaseReturn) {
        PurchaseReturnEntity entity = new PurchaseReturnEntity();
        fill(entity, purchaseReturn);
        returnMapper.insert(entity);
        purchaseReturn.setId(entity.getId());
        for (PurchaseReturnLine line : purchaseReturn.getLines()) {
            PurchaseReturnLineEntity lineEntity = new PurchaseReturnLineEntity();
            lineEntity.setReturnId(entity.getId());
            fillLine(lineEntity, line);
            returnLineMapper.insert(lineEntity);
        }
    }

    @Override
    public void update(PurchaseReturn purchaseReturn) {
        PurchaseReturnEntity entity = new PurchaseReturnEntity();
        entity.setId(purchaseReturn.getId());
        entity.setStatus(purchaseReturn.getStatus());
        entity.setTotalAmount(purchaseReturn.getTotalAmount());
        returnMapper.updateById(entity);
        for (PurchaseReturnLine line : purchaseReturn.getLines()) {
            PurchaseReturnLineEntity lineEntity = returnLineMapper.selectOne(
                    new LambdaQueryWrapper<PurchaseReturnLineEntity>()
                            .eq(PurchaseReturnLineEntity::getReturnId, purchaseReturn.getId())
                            .eq(PurchaseReturnLineEntity::getLineNo, line.getLineNo())
                            .last("limit 1"));
            if (lineEntity != null) {
                lineEntity.setUnitCost(line.getUnitCost());
                lineEntity.setCostAmount(line.getCostAmount());
                lineEntity.setAmount(line.getAmount());
                returnLineMapper.updateById(lineEntity);
            }
        }
    }

    @Override
    public BigDecimal sumReturnedQuantity(Long purchaseOrderId, Integer orderLineNo) {
        List<Long> returnIds = returnMapper.selectList(new LambdaQueryWrapper<PurchaseReturnEntity>()
                        .eq(PurchaseReturnEntity::getPurchaseOrderId, purchaseOrderId)
                        .ne(PurchaseReturnEntity::getStatus, ScmStatus.CANCELLED))
                .stream().map(PurchaseReturnEntity::getId).toList();
        if (returnIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return returnLineMapper.selectList(new LambdaQueryWrapper<PurchaseReturnLineEntity>()
                        .in(PurchaseReturnLineEntity::getReturnId, returnIds)
                        .eq(PurchaseReturnLineEntity::getOrderLineNo, orderLineNo))
                .stream()
                .map(PurchaseReturnLineEntity::getQuantity)
                .filter(quantity -> quantity != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<PurchaseReturnEntity> filter(ScmQuery query) {
        LambdaQueryWrapper<PurchaseReturnEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(PurchaseReturnEntity::getDocNo, query.getKeyword())
                    .or().like(PurchaseReturnEntity::getSupplierName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PurchaseReturnEntity::getStatus, query.getStatus());
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(PurchaseReturnEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(PurchaseReturnEntity::getDocDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(PurchaseReturnEntity::getDocDate, query.getToDate());
        }
        return wrapper.orderByDesc(PurchaseReturnEntity::getDocDate)
                .orderByDesc(PurchaseReturnEntity::getDocNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private PurchaseReturn toDomain(PurchaseReturnEntity entity) {
        PurchaseReturn purchaseReturn = new PurchaseReturn();
        purchaseReturn.setId(entity.getId());
        purchaseReturn.setDocNo(entity.getDocNo());
        purchaseReturn.setPurchaseOrderId(entity.getPurchaseOrderId());
        purchaseReturn.setSupplierId(entity.getSupplierId());
        purchaseReturn.setSupplierName(entity.getSupplierName());
        purchaseReturn.setDocDate(entity.getDocDate());
        purchaseReturn.setWarehouseId(entity.getWarehouseId());
        purchaseReturn.setStatus(entity.getStatus());
        purchaseReturn.setCurrency(entity.getCurrency());
        purchaseReturn.setTotalAmount(entity.getTotalAmount());
        purchaseReturn.setRemark(entity.getRemark());
        return purchaseReturn;
    }

    /**
     * 分录实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private PurchaseReturnLine toDomain(PurchaseReturnLineEntity entity) {
        PurchaseReturnLine line = new PurchaseReturnLine();
        line.setLineNo(entity.getLineNo());
        line.setOrderLineNo(entity.getOrderLineNo());
        line.setItemId(entity.getItemId());
        line.setItemCode(entity.getItemCode());
        line.setItemName(entity.getItemName());
        line.setUom(entity.getUom());
        line.setQuantity(entity.getQuantity());
        line.setUnitPrice(entity.getUnitPrice());
        line.setAmount(entity.getAmount());
        line.setUnitCost(entity.getUnitCost());
        line.setCostAmount(entity.getCostAmount());
        return line;
    }

    /**
     * 领域模型写入单头实体。
     *
     * @param entity 实体
     * @param purchaseReturn 领域模型
     */
    private void fill(PurchaseReturnEntity entity, PurchaseReturn purchaseReturn) {
        entity.setDocNo(purchaseReturn.getDocNo());
        entity.setPurchaseOrderId(purchaseReturn.getPurchaseOrderId());
        entity.setSupplierId(purchaseReturn.getSupplierId());
        entity.setSupplierName(purchaseReturn.getSupplierName());
        entity.setDocDate(purchaseReturn.getDocDate());
        entity.setWarehouseId(purchaseReturn.getWarehouseId());
        entity.setStatus(purchaseReturn.getStatus());
        entity.setCurrency(purchaseReturn.getCurrency());
        entity.setTotalAmount(purchaseReturn.getTotalAmount());
        entity.setRemark(purchaseReturn.getRemark());
    }

    /**
     * 领域模型写入分录实体。
     *
     * @param entity 实体
     * @param line 领域模型
     */
    private void fillLine(PurchaseReturnLineEntity entity, PurchaseReturnLine line) {
        entity.setLineNo(line.getLineNo());
        entity.setOrderLineNo(line.getOrderLineNo());
        entity.setItemId(line.getItemId());
        entity.setItemCode(line.getItemCode());
        entity.setItemName(line.getItemName());
        entity.setUom(line.getUom());
        entity.setQuantity(line.getQuantity());
        entity.setUnitPrice(line.getUnitPrice());
        entity.setAmount(line.getAmount());
        entity.setUnitCost(line.getUnitCost());
        entity.setCostAmount(line.getCostAmount());
    }
}
