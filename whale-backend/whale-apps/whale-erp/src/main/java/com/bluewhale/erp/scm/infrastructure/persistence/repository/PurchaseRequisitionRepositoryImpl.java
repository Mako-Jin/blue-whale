package com.bluewhale.erp.scm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.scm.domain.model.PurchaseRequisition;
import com.bluewhale.erp.scm.domain.model.PurchaseRequisitionLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.repository.PurchaseRequisitionRepository;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.PurchaseRequisitionEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.PurchaseRequisitionLineEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.PurchaseRequisitionLineMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.PurchaseRequisitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 采购申请单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PurchaseRequisitionRepositoryImpl implements PurchaseRequisitionRepository {

    /** 申请单号前缀 */
    private static final String DOC_PREFIX = "PA-";

    /** 单号日期格式 */
    private static final DateTimeFormatter DOC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PurchaseRequisitionMapper requisitionMapper;

    private final PurchaseRequisitionLineMapper requisitionLineMapper;

    @Override
    public List<PurchaseRequisition> page(ScmQuery query) {
        Page<PurchaseRequisitionEntity> page = requisitionMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ScmQuery query) {
        return requisitionMapper.selectCount(filter(query));
    }

    @Override
    public Optional<PurchaseRequisition> findById(Long id) {
        PurchaseRequisitionEntity entity = requisitionMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        PurchaseRequisition requisition = toDomain(entity);
        requisition.setLines(requisitionLineMapper.selectList(
                        new LambdaQueryWrapper<PurchaseRequisitionLineEntity>()
                                .eq(PurchaseRequisitionLineEntity::getRequisitionId, id)
                                .orderByAsc(PurchaseRequisitionLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(requisition);
    }

    @Override
    public void insert(PurchaseRequisition requisition) {
        PurchaseRequisitionEntity entity = new PurchaseRequisitionEntity();
        fill(entity, requisition);
        requisitionMapper.insert(entity);
        requisition.setId(entity.getId());
        for (PurchaseRequisitionLine line : requisition.getLines()) {
            PurchaseRequisitionLineEntity lineEntity = new PurchaseRequisitionLineEntity();
            lineEntity.setRequisitionId(entity.getId());
            fillLine(lineEntity, line);
            requisitionLineMapper.insert(lineEntity);
        }
    }

    @Override
    public void update(PurchaseRequisition requisition) {
        PurchaseRequisitionEntity entity = new PurchaseRequisitionEntity();
        entity.setId(requisition.getId());
        entity.setStatus(requisition.getStatus());
        entity.setTotalAmount(requisition.getTotalAmount());
        entity.setConvertedOrderNo(requisition.getConvertedOrderNo());
        requisitionMapper.updateById(entity);
    }

    @Override
    public String nextDocNo(LocalDate docDate) {
        long sequence = requisitionMapper.selectCount(new LambdaQueryWrapper<PurchaseRequisitionEntity>()
                .eq(PurchaseRequisitionEntity::getDocDate, docDate)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, docDate.format(DOC_DATE_FORMATTER), sequence);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<PurchaseRequisitionEntity> filter(ScmQuery query) {
        LambdaQueryWrapper<PurchaseRequisitionEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(PurchaseRequisitionEntity::getDocNo, query.getKeyword())
                    .or().like(PurchaseRequisitionEntity::getSupplierName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PurchaseRequisitionEntity::getStatus, query.getStatus());
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(PurchaseRequisitionEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(PurchaseRequisitionEntity::getDocDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(PurchaseRequisitionEntity::getDocDate, query.getToDate());
        }
        return wrapper.orderByDesc(PurchaseRequisitionEntity::getDocDate)
                .orderByDesc(PurchaseRequisitionEntity::getDocNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private PurchaseRequisition toDomain(PurchaseRequisitionEntity entity) {
        PurchaseRequisition requisition = new PurchaseRequisition();
        requisition.setId(entity.getId());
        requisition.setDocNo(entity.getDocNo());
        requisition.setSupplierId(entity.getSupplierId());
        requisition.setSupplierName(entity.getSupplierName());
        requisition.setDocDate(entity.getDocDate());
        requisition.setRequiredDate(entity.getRequiredDate());
        requisition.setWarehouseId(entity.getWarehouseId());
        requisition.setStatus(entity.getStatus());
        requisition.setCurrency(entity.getCurrency());
        requisition.setTotalAmount(entity.getTotalAmount());
        requisition.setConvertedOrderNo(entity.getConvertedOrderNo());
        requisition.setRemark(entity.getRemark());
        return requisition;
    }

    /**
     * 分录实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private PurchaseRequisitionLine toDomain(PurchaseRequisitionLineEntity entity) {
        PurchaseRequisitionLine line = new PurchaseRequisitionLine();
        line.setLineNo(entity.getLineNo());
        line.setItemId(entity.getItemId());
        line.setItemCode(entity.getItemCode());
        line.setItemName(entity.getItemName());
        line.setUom(entity.getUom());
        line.setQuantity(entity.getQuantity());
        line.setUnitPrice(entity.getUnitPrice());
        line.setAmount(entity.getAmount());
        return line;
    }

    /**
     * 领域模型写入单头实体。
     *
     * @param entity 实体
     * @param requisition 领域模型
     */
    private void fill(PurchaseRequisitionEntity entity, PurchaseRequisition requisition) {
        entity.setDocNo(requisition.getDocNo());
        entity.setSupplierId(requisition.getSupplierId());
        entity.setSupplierName(requisition.getSupplierName());
        entity.setDocDate(requisition.getDocDate());
        entity.setRequiredDate(requisition.getRequiredDate());
        entity.setWarehouseId(requisition.getWarehouseId());
        entity.setStatus(requisition.getStatus());
        entity.setCurrency(requisition.getCurrency());
        entity.setTotalAmount(requisition.getTotalAmount());
        entity.setConvertedOrderNo(requisition.getConvertedOrderNo());
        entity.setRemark(requisition.getRemark());
    }

    /**
     * 领域模型写入分录实体。
     *
     * @param entity 实体
     * @param line 领域模型
     */
    private void fillLine(PurchaseRequisitionLineEntity entity, PurchaseRequisitionLine line) {
        entity.setLineNo(line.getLineNo());
        entity.setItemId(line.getItemId());
        entity.setItemCode(line.getItemCode());
        entity.setItemName(line.getItemName());
        entity.setUom(line.getUom());
        entity.setQuantity(line.getQuantity());
        entity.setUnitPrice(line.getUnitPrice());
        entity.setAmount(line.getAmount());
    }
}
