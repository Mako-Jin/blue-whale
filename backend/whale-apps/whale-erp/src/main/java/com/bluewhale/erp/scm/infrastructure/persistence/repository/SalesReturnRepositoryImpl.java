package com.bluewhale.erp.scm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.scm.domain.model.SalesReturn;
import com.bluewhale.erp.scm.domain.model.SalesReturnLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.repository.SalesReturnRepository;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.SalesReturnEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.SalesReturnLineEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.SalesReturnLineMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.SalesReturnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 销售退货单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SalesReturnRepositoryImpl implements SalesReturnRepository {

    /** 退货单号前缀 */
    private static final String DOC_PREFIX = "SR-";

    /** 单号日期格式 */
    private static final DateTimeFormatter DOC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SalesReturnMapper returnMapper;

    private final SalesReturnLineMapper returnLineMapper;

    @Override
    public List<SalesReturn> page(ScmQuery query) {
        Page<SalesReturnEntity> page = returnMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ScmQuery query) {
        return returnMapper.selectCount(filter(query));
    }

    @Override
    public Optional<SalesReturn> findById(Long id) {
        SalesReturnEntity entity = returnMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        SalesReturn salesReturn = toDomain(entity);
        salesReturn.setLines(returnLineMapper.selectList(new LambdaQueryWrapper<SalesReturnLineEntity>()
                        .eq(SalesReturnLineEntity::getReturnId, id)
                        .orderByAsc(SalesReturnLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(salesReturn);
    }

    @Override
    public Optional<SalesReturn> findByCrmReturnNo(String crmReturnNo) {
        SalesReturnEntity entity = returnMapper.selectOne(new LambdaQueryWrapper<SalesReturnEntity>()
                .eq(SalesReturnEntity::getCrmReturnNo, crmReturnNo)
                .last("limit 1"));
        if (entity == null) {
            return Optional.empty();
        }
        SalesReturn salesReturn = toDomain(entity);
        salesReturn.setLines(returnLineMapper.selectList(new LambdaQueryWrapper<SalesReturnLineEntity>()
                        .eq(SalesReturnLineEntity::getReturnId, entity.getId())
                        .orderByAsc(SalesReturnLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(salesReturn);
    }

    @Override
    public String nextDocNo(LocalDate docDate) {
        long sequence = returnMapper.selectCount(new LambdaQueryWrapper<SalesReturnEntity>()
                .eq(SalesReturnEntity::getDocDate, docDate)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, docDate.format(DOC_DATE_FORMATTER), sequence);
    }

    @Override
    public void insert(SalesReturn salesReturn) {
        SalesReturnEntity entity = new SalesReturnEntity();
        fill(entity, salesReturn);
        returnMapper.insert(entity);
        salesReturn.setId(entity.getId());
        for (SalesReturnLine line : salesReturn.getLines()) {
            SalesReturnLineEntity lineEntity = new SalesReturnLineEntity();
            lineEntity.setReturnId(entity.getId());
            fillLine(lineEntity, line);
            returnLineMapper.insert(lineEntity);
        }
    }

    @Override
    public void update(SalesReturn salesReturn) {
        SalesReturnEntity entity = new SalesReturnEntity();
        entity.setId(salesReturn.getId());
        entity.setStatus(salesReturn.getStatus());
        entity.setTotalAmount(salesReturn.getTotalAmount());
        returnMapper.updateById(entity);
        for (SalesReturnLine line : salesReturn.getLines()) {
            SalesReturnLineEntity lineEntity = returnLineMapper.selectOne(
                    new LambdaQueryWrapper<SalesReturnLineEntity>()
                            .eq(SalesReturnLineEntity::getReturnId, salesReturn.getId())
                            .eq(SalesReturnLineEntity::getLineNo, line.getLineNo())
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
    public BigDecimal sumReturnedQuantity(Long salesOrderId, Integer orderLineNo) {
        List<Long> returnIds = returnMapper.selectList(new LambdaQueryWrapper<SalesReturnEntity>()
                        .eq(SalesReturnEntity::getSalesOrderId, salesOrderId)
                        .ne(SalesReturnEntity::getStatus, ScmStatus.CANCELLED))
                .stream().map(SalesReturnEntity::getId).toList();
        if (returnIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return returnLineMapper.selectList(new LambdaQueryWrapper<SalesReturnLineEntity>()
                        .in(SalesReturnLineEntity::getReturnId, returnIds)
                        .eq(SalesReturnLineEntity::getOrderLineNo, orderLineNo))
                .stream()
                .map(SalesReturnLineEntity::getQuantity)
                .filter(quantity -> quantity != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<SalesReturnEntity> filter(ScmQuery query) {
        LambdaQueryWrapper<SalesReturnEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(SalesReturnEntity::getDocNo, query.getKeyword())
                    .or().like(SalesReturnEntity::getCustomerName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(SalesReturnEntity::getStatus, query.getStatus());
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(SalesReturnEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(SalesReturnEntity::getDocDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(SalesReturnEntity::getDocDate, query.getToDate());
        }
        return wrapper.orderByDesc(SalesReturnEntity::getDocDate)
                .orderByDesc(SalesReturnEntity::getDocNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private SalesReturn toDomain(SalesReturnEntity entity) {
        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setId(entity.getId());
        salesReturn.setDocNo(entity.getDocNo());
        salesReturn.setSalesOrderId(entity.getSalesOrderId());
        salesReturn.setCustomerId(entity.getCustomerId());
        salesReturn.setCustomerName(entity.getCustomerName());
        salesReturn.setDocDate(entity.getDocDate());
        salesReturn.setWarehouseId(entity.getWarehouseId());
        salesReturn.setStatus(entity.getStatus());
        salesReturn.setCurrency(entity.getCurrency());
        salesReturn.setTotalAmount(entity.getTotalAmount());
        salesReturn.setRemark(entity.getRemark());
        salesReturn.setCrmReturnNo(entity.getCrmReturnNo());
        salesReturn.setSourceType(entity.getSourceType());
        salesReturn.setSourceId(entity.getSourceId());
        return salesReturn;
    }

    /**
     * 分录实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private SalesReturnLine toDomain(SalesReturnLineEntity entity) {
        SalesReturnLine line = new SalesReturnLine();
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
     * @param salesReturn 领域模型
     */
    private void fill(SalesReturnEntity entity, SalesReturn salesReturn) {
        entity.setDocNo(salesReturn.getDocNo());
        entity.setSalesOrderId(salesReturn.getSalesOrderId());
        entity.setCustomerId(salesReturn.getCustomerId());
        entity.setCustomerName(salesReturn.getCustomerName());
        entity.setDocDate(salesReturn.getDocDate());
        entity.setWarehouseId(salesReturn.getWarehouseId());
        entity.setStatus(salesReturn.getStatus());
        entity.setCurrency(salesReturn.getCurrency());
        entity.setTotalAmount(salesReturn.getTotalAmount());
        entity.setRemark(salesReturn.getRemark());
        entity.setCrmReturnNo(salesReturn.getCrmReturnNo());
        entity.setSourceType(salesReturn.getSourceType());
        entity.setSourceId(salesReturn.getSourceId());
    }

    /**
     * 领域模型写入分录实体。
     *
     * @param entity 实体
     * @param line 领域模型
     */
    private void fillLine(SalesReturnLineEntity entity, SalesReturnLine line) {
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
