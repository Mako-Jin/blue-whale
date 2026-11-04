package com.bluewhale.erp.scm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.scm.domain.model.SalesQuotation;
import com.bluewhale.erp.scm.domain.model.SalesQuotationLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.repository.SalesQuotationRepository;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.SalesQuotationEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.SalesQuotationLineEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.SalesQuotationLineMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.SalesQuotationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 销售报价单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SalesQuotationRepositoryImpl implements SalesQuotationRepository {

    /** 报价单号前缀 */
    private static final String DOC_PREFIX = "SQ-";

    /** 单号日期格式 */
    private static final DateTimeFormatter DOC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SalesQuotationMapper quotationMapper;

    private final SalesQuotationLineMapper quotationLineMapper;

    @Override
    public List<SalesQuotation> page(ScmQuery query) {
        Page<SalesQuotationEntity> page = quotationMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ScmQuery query) {
        return quotationMapper.selectCount(filter(query));
    }

    @Override
    public Optional<SalesQuotation> findById(Long id) {
        SalesQuotationEntity entity = quotationMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        SalesQuotation quotation = toDomain(entity);
        quotation.setLines(quotationLineMapper.selectList(new LambdaQueryWrapper<SalesQuotationLineEntity>()
                        .eq(SalesQuotationLineEntity::getQuotationId, id)
                        .orderByAsc(SalesQuotationLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(quotation);
    }

    @Override
    public void insert(SalesQuotation quotation) {
        SalesQuotationEntity entity = new SalesQuotationEntity();
        fill(entity, quotation);
        quotationMapper.insert(entity);
        quotation.setId(entity.getId());
        for (SalesQuotationLine line : quotation.getLines()) {
            SalesQuotationLineEntity lineEntity = new SalesQuotationLineEntity();
            lineEntity.setQuotationId(entity.getId());
            fillLine(lineEntity, line);
            quotationLineMapper.insert(lineEntity);
        }
    }

    @Override
    public void update(SalesQuotation quotation) {
        SalesQuotationEntity entity = new SalesQuotationEntity();
        entity.setId(quotation.getId());
        entity.setStatus(quotation.getStatus());
        entity.setTotalAmount(quotation.getTotalAmount());
        entity.setConvertedOrderNo(quotation.getConvertedOrderNo());
        quotationMapper.updateById(entity);
    }

    @Override
    public String nextDocNo(LocalDate docDate) {
        long sequence = quotationMapper.selectCount(new LambdaQueryWrapper<SalesQuotationEntity>()
                .eq(SalesQuotationEntity::getDocDate, docDate)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, docDate.format(DOC_DATE_FORMATTER), sequence);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<SalesQuotationEntity> filter(ScmQuery query) {
        LambdaQueryWrapper<SalesQuotationEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(SalesQuotationEntity::getDocNo, query.getKeyword())
                    .or().like(SalesQuotationEntity::getCustomerName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(SalesQuotationEntity::getStatus, query.getStatus());
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(SalesQuotationEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(SalesQuotationEntity::getDocDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(SalesQuotationEntity::getDocDate, query.getToDate());
        }
        return wrapper.orderByDesc(SalesQuotationEntity::getDocDate)
                .orderByDesc(SalesQuotationEntity::getDocNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private SalesQuotation toDomain(SalesQuotationEntity entity) {
        SalesQuotation quotation = new SalesQuotation();
        quotation.setId(entity.getId());
        quotation.setDocNo(entity.getDocNo());
        quotation.setCustomerId(entity.getCustomerId());
        quotation.setCustomerName(entity.getCustomerName());
        quotation.setDocDate(entity.getDocDate());
        quotation.setValidUntil(entity.getValidUntil());
        quotation.setWarehouseId(entity.getWarehouseId());
        quotation.setStatus(entity.getStatus());
        quotation.setCurrency(entity.getCurrency());
        quotation.setTotalAmount(entity.getTotalAmount());
        quotation.setConvertedOrderNo(entity.getConvertedOrderNo());
        quotation.setRemark(entity.getRemark());
        return quotation;
    }

    /**
     * 分录实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private SalesQuotationLine toDomain(SalesQuotationLineEntity entity) {
        SalesQuotationLine line = new SalesQuotationLine();
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
     * @param quotation 领域模型
     */
    private void fill(SalesQuotationEntity entity, SalesQuotation quotation) {
        entity.setDocNo(quotation.getDocNo());
        entity.setCustomerId(quotation.getCustomerId());
        entity.setCustomerName(quotation.getCustomerName());
        entity.setDocDate(quotation.getDocDate());
        entity.setValidUntil(quotation.getValidUntil());
        entity.setWarehouseId(quotation.getWarehouseId());
        entity.setStatus(quotation.getStatus());
        entity.setCurrency(quotation.getCurrency());
        entity.setTotalAmount(quotation.getTotalAmount());
        entity.setConvertedOrderNo(quotation.getConvertedOrderNo());
        entity.setRemark(quotation.getRemark());
    }

    /**
     * 领域模型写入分录实体。
     *
     * @param entity 实体
     * @param line 领域模型
     */
    private void fillLine(SalesQuotationLineEntity entity, SalesQuotationLine line) {
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
