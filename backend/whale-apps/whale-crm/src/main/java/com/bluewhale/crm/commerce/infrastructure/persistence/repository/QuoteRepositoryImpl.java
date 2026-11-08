package com.bluewhale.crm.commerce.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.commerce.domain.model.Quote;
import com.bluewhale.crm.commerce.domain.model.QuoteLine;
import com.bluewhale.crm.commerce.domain.model.QuoteQuery;
import com.bluewhale.crm.commerce.domain.model.QuoteStatus;
import com.bluewhale.crm.commerce.domain.repository.QuoteRepository;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.QuoteEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.QuoteLineEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.QuoteLineMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.QuoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报价仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class QuoteRepositoryImpl implements QuoteRepository {

    private final QuoteMapper quoteMapper;

    private final QuoteLineMapper quoteLineMapper;

    @Override
    public List<Quote> page(QuoteQuery query) {
        Page<QuoteEntity> page = quoteMapper.selectPage(query.toPage(), buildWrapper(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(QuoteQuery query) {
        return quoteMapper.selectCount(buildWrapper(query));
    }

    @Override
    public Quote findById(Long id) {
        QuoteEntity entity = quoteMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public List<QuoteLine> findLines(Long quoteId) {
        return quoteLineMapper.selectList(new LambdaQueryWrapper<QuoteLineEntity>()
                        .eq(QuoteLineEntity::getQuoteId, quoteId)
                        .orderByAsc(QuoteLineEntity::getLineNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(Quote quote) {
        QuoteEntity entity = toEntity(quote);
        if (entity.getVersion() == null) {
            entity.setVersion(1);
        }
        if (entity.getLockVersion() == null) {
            entity.setLockVersion(0);
        }
        quoteMapper.insert(entity);
        quote.setId(entity.getId());
        quote.setVersion(entity.getVersion());
        quote.setLockVersion(entity.getLockVersion());
        quote.setCreateTime(entity.getCreateTime());
    }

    @Override
    public void bindRoot(Long id, Long rootQuoteId) {
        LambdaUpdateWrapper<QuoteEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(QuoteEntity::getId, id)
                .set(QuoteEntity::getRootQuoteId, rootQuoteId);
        quoteMapper.update(null, wrapper);
    }

    @Override
    public void replaceLines(Long quoteId, List<QuoteLine> lines) {
        // 行表挂 BaseEntity 的 @TableLogic：BaseMapper.delete 是逻辑删除，旧行仍物理存在，
        // 会顶住报价行唯一键导致重插冲突；明细行随主单留痕即可，改物理删除。
        quoteLineMapper.deletePhysicallyByQuoteId(quoteId);
        for (QuoteLine line : lines) {
            QuoteLineEntity entity = toEntity(line);
            entity.setQuoteId(quoteId);
            quoteLineMapper.insert(entity);
            line.setId(entity.getId());
            line.setQuoteId(quoteId);
        }
    }

    @Override
    public boolean update(Quote quote) {
        QuoteEntity entity = toEntity(quote);
        entity.setId(quote.getId());
        if (entity.getLockVersion() == null) {
            entity.setLockVersion(0);
        }
        boolean updated = quoteMapper.updateById(entity) > 0;
        if (updated) {
            quote.setLockVersion(entity.getLockVersion());
        }
        return updated;
    }

    @Override
    public boolean applyApprovalResult(Long quoteId, boolean approved, LocalDateTime at) {
        LambdaUpdateWrapper<QuoteEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(QuoteEntity::getId, quoteId)
                .eq(QuoteEntity::getStatus, QuoteStatus.PENDING_APPROVAL.name())
                .set(QuoteEntity::getStatus, approved ? QuoteStatus.APPROVED.name() : QuoteStatus.REJECTED.name())
                .set(QuoteEntity::getUpdateTime, at);
        if (approved) {
            wrapper.set(QuoteEntity::getApprovedAt, at);
        }
        return quoteMapper.update(null, wrapper) > 0;
    }

    @Override
    public long maxSequence(String prefix) {
        return quoteMapper.maxSequence(prefix);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<QuoteEntity> buildWrapper(QuoteQuery query) {
        LambdaQueryWrapper<QuoteEntity> wrapper = new LambdaQueryWrapper<>();
        String keyword = query.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(inner -> inner.like(QuoteEntity::getQuoteNo, trimmed)
                    .or().like(QuoteEntity::getTitle, trimmed));
        }
        wrapper.eq(query.getOpportunityId() != null, QuoteEntity::getOpportunityId, query.getOpportunityId());
        wrapper.eq(query.getCustomerId() != null, QuoteEntity::getCustomerId, query.getCustomerId());
        wrapper.eq(query.getStatus() != null && !query.getStatus().isBlank(),
                QuoteEntity::getStatus, query.getStatus());
        wrapper.eq(query.getOwnerId() != null, QuoteEntity::getOwnerId, query.getOwnerId());
        wrapper.orderByDesc(QuoteEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Quote toDomain(QuoteEntity entity) {
        Quote quote = new Quote();
        quote.setId(entity.getId());
        quote.setQuoteNo(entity.getQuoteNo());
        quote.setVersion(entity.getVersion());
        quote.setRootQuoteId(entity.getRootQuoteId());
        quote.setParentQuoteId(entity.getParentQuoteId());
        quote.setOpportunityId(entity.getOpportunityId());
        quote.setCustomerId(entity.getCustomerId());
        quote.setTitle(entity.getTitle());
        quote.setStatus(QuoteStatus.from(entity.getStatus()));
        quote.setCurrency(entity.getCurrency());
        quote.setValidUntil(entity.getValidUntil());
        quote.setSubtotalAmount(entity.getSubtotalAmount());
        quote.setTaxAmount(entity.getTaxAmount());
        quote.setDiscountAmount(entity.getDiscountAmount());
        quote.setTotalAmount(entity.getTotalAmount());
        quote.setDiscountRate(entity.getDiscountRate());
        quote.setRemark(entity.getRemark());
        quote.setOwnerId(entity.getOwnerId());
        quote.setDeptId(entity.getDeptId());
        quote.setApprovalInstanceId(entity.getApprovalInstanceId());
        quote.setSubmittedAt(entity.getSubmittedAt());
        quote.setApprovedAt(entity.getApprovedAt());
        quote.setLockVersion(entity.getLockVersion());
        quote.setCreateTime(entity.getCreateTime());
        quote.setUpdateTime(entity.getUpdateTime());
        return quote;
    }

    /**
     * 领域模型转实体。
     *
     * @param quote 领域模型
     * @return 实体
     */
    private QuoteEntity toEntity(Quote quote) {
        QuoteEntity entity = new QuoteEntity();
        entity.setQuoteNo(quote.getQuoteNo());
        entity.setVersion(quote.getVersion());
        entity.setRootQuoteId(quote.getRootQuoteId());
        entity.setParentQuoteId(quote.getParentQuoteId());
        entity.setOpportunityId(quote.getOpportunityId());
        entity.setCustomerId(quote.getCustomerId());
        entity.setTitle(quote.getTitle());
        entity.setStatus(quote.getStatus() == null ? null : quote.getStatus().name());
        entity.setCurrency(quote.getCurrency());
        entity.setValidUntil(quote.getValidUntil());
        entity.setSubtotalAmount(quote.getSubtotalAmount());
        entity.setTaxAmount(quote.getTaxAmount());
        entity.setDiscountAmount(quote.getDiscountAmount());
        entity.setTotalAmount(quote.getTotalAmount());
        entity.setDiscountRate(quote.getDiscountRate());
        entity.setRemark(quote.getRemark());
        entity.setOwnerId(quote.getOwnerId());
        entity.setDeptId(quote.getDeptId());
        entity.setApprovalInstanceId(quote.getApprovalInstanceId());
        entity.setSubmittedAt(quote.getSubmittedAt());
        entity.setApprovedAt(quote.getApprovedAt());
        entity.setLockVersion(quote.getLockVersion());
        return entity;
    }

    /**
     * 报价行实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private QuoteLine toDomain(QuoteLineEntity entity) {
        QuoteLine line = new QuoteLine();
        line.setId(entity.getId());
        line.setQuoteId(entity.getQuoteId());
        line.setLineNo(entity.getLineNo());
        line.setProductName(entity.getProductName());
        line.setSpecification(entity.getSpecification());
        line.setUnit(entity.getUnit());
        line.setQuantity(entity.getQuantity());
        line.setUnitPrice(entity.getUnitPrice());
        line.setDiscountRate(entity.getDiscountRate());
        line.setTaxRate(entity.getTaxRate());
        line.setLineAmount(entity.getLineAmount());
        line.setRemark(entity.getRemark());
        return line;
    }

    /**
     * 报价行领域模型转实体。
     *
     * @param line 领域模型
     * @return 实体
     */
    private QuoteLineEntity toEntity(QuoteLine line) {
        QuoteLineEntity entity = new QuoteLineEntity();
        entity.setQuoteId(line.getQuoteId());
        entity.setLineNo(line.getLineNo());
        entity.setProductName(line.getProductName());
        entity.setSpecification(line.getSpecification());
        entity.setUnit(line.getUnit());
        entity.setQuantity(line.getQuantity());
        entity.setUnitPrice(line.getUnitPrice());
        entity.setDiscountRate(line.getDiscountRate());
        entity.setTaxRate(line.getTaxRate());
        entity.setLineAmount(line.getLineAmount());
        entity.setRemark(line.getRemark());
        return entity;
    }
}
