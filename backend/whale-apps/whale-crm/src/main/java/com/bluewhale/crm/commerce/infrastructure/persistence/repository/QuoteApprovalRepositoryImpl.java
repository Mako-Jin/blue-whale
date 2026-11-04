package com.bluewhale.crm.commerce.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.crm.commerce.domain.model.QuoteApproval;
import com.bluewhale.crm.commerce.domain.model.QuoteApprovalStatus;
import com.bluewhale.crm.commerce.domain.repository.QuoteApprovalRepository;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.QuoteApprovalEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.QuoteApprovalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 报价审批记录仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class QuoteApprovalRepositoryImpl implements QuoteApprovalRepository {

    private final QuoteApprovalMapper approvalMapper;

    @Override
    public void insert(QuoteApproval approval) {
        QuoteApprovalEntity entity = new QuoteApprovalEntity();
        entity.setQuoteId(approval.getQuoteId());
        entity.setQuoteVersion(approval.getQuoteVersion());
        entity.setInstanceId(approval.getInstanceId());
        entity.setStatus(approval.getStatus() == null ? null : approval.getStatus().name());
        entity.setApprovalLevel(approval.getApprovalLevel());
        entity.setDiscountRate(approval.getDiscountRate());
        entity.setComment(approval.getComment());
        entity.setSubmitterId(approval.getSubmitterId());
        entity.setSubmittedAt(approval.getSubmittedAt());
        entity.setDecidedAt(approval.getDecidedAt());
        approvalMapper.insert(entity);
        approval.setId(entity.getId());
    }

    @Override
    public QuoteApproval findPendingByInstanceId(Long instanceId) {
        QuoteApprovalEntity entity = approvalMapper.selectOne(new LambdaQueryWrapper<QuoteApprovalEntity>()
                .eq(QuoteApprovalEntity::getInstanceId, instanceId)
                .eq(QuoteApprovalEntity::getStatus, QuoteApprovalStatus.PENDING.name())
                .last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        QuoteApproval approval = new QuoteApproval();
        approval.setId(entity.getId());
        approval.setQuoteId(entity.getQuoteId());
        approval.setQuoteVersion(entity.getQuoteVersion());
        approval.setInstanceId(entity.getInstanceId());
        approval.setStatus(QuoteApprovalStatus.from(entity.getStatus()));
        approval.setApprovalLevel(entity.getApprovalLevel());
        approval.setDiscountRate(entity.getDiscountRate());
        approval.setComment(entity.getComment());
        approval.setSubmitterId(entity.getSubmitterId());
        approval.setSubmittedAt(entity.getSubmittedAt());
        approval.setDecidedAt(entity.getDecidedAt());
        return approval;
    }

    @Override
    public boolean markDecided(Long id, QuoteApprovalStatus status, String comment, LocalDateTime at) {
        LambdaUpdateWrapper<QuoteApprovalEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(QuoteApprovalEntity::getId, id)
                .eq(QuoteApprovalEntity::getStatus, QuoteApprovalStatus.PENDING.name())
                .set(QuoteApprovalEntity::getStatus, status.name())
                .set(QuoteApprovalEntity::getComment, comment)
                .set(QuoteApprovalEntity::getDecidedAt, at)
                .set(QuoteApprovalEntity::getUpdateTime, at);
        return approvalMapper.update(null, wrapper) > 0;
    }
}
