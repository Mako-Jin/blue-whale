package com.bluewhale.oa.approval.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.oa.approval.domain.model.ApprovalEffect;
import com.bluewhale.oa.approval.domain.model.ApprovalEffectStatus;
import com.bluewhale.oa.approval.domain.repository.ApprovalEffectRepository;
import com.bluewhale.oa.approval.infrastructure.persistence.entity.ApprovalEffectEntity;
import com.bluewhale.oa.approval.infrastructure.persistence.mapper.ApprovalEffectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 审批业务副作用仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ApprovalEffectRepositoryImpl implements ApprovalEffectRepository {

    private final ApprovalEffectMapper approvalEffectMapper;

    @Override
    public List<ApprovalEffect> listByInstance(Long approvalInstanceId) {
        return approvalEffectMapper.selectList(new LambdaQueryWrapper<ApprovalEffectEntity>()
                        .eq(ApprovalEffectEntity::getApprovalInstanceId, approvalInstanceId)
                        .orderByAsc(ApprovalEffectEntity::getCreateTime))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ApprovalEffect> findByInstanceAndType(Long approvalInstanceId, String effectType) {
        return Optional.ofNullable(approvalEffectMapper.selectOne(new LambdaQueryWrapper<ApprovalEffectEntity>()
                .eq(ApprovalEffectEntity::getApprovalInstanceId, approvalInstanceId)
                .eq(ApprovalEffectEntity::getEffectType, effectType)
                .last("limit 1"))).map(this::toDomain);
    }

    @Override
    public boolean insertIfAbsent(ApprovalEffect effect) {
        if (findByInstanceAndType(effect.getApprovalInstanceId(), effect.getEffectType()).isPresent()) {
            return false;
        }
        try {
            ApprovalEffectEntity entity = new ApprovalEffectEntity();
            entity.setApprovalInstanceId(effect.getApprovalInstanceId());
            entity.setEffectType(effect.getEffectType());
            entity.setEffectStatus(ApprovalEffectStatus.PENDING.name());
            entity.setPayload(effect.getPayload());
            entity.setRetryCount(0);
            approvalEffectMapper.insert(entity);
            effect.setId(entity.getId());
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public void update(ApprovalEffect effect) {
        approvalEffectMapper.update(null, new LambdaUpdateWrapper<ApprovalEffectEntity>()
                .eq(ApprovalEffectEntity::getId, effect.getId())
                .set(ApprovalEffectEntity::getEffectStatus,
                        effect.getEffectStatus() == null ? null : effect.getEffectStatus().name())
                .set(ApprovalEffectEntity::getExecutedAt, effect.getExecutedAt())
                .set(ApprovalEffectEntity::getRetryCount, effect.getRetryCount())
                .set(ApprovalEffectEntity::getErrorMsg, effect.getErrorMsg()));
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ApprovalEffect toDomain(ApprovalEffectEntity entity) {
        ApprovalEffect effect = new ApprovalEffect();
        effect.setId(entity.getId());
        effect.setApprovalInstanceId(entity.getApprovalInstanceId());
        effect.setEffectType(entity.getEffectType());
        effect.setEffectStatus(entity.getEffectStatus() == null
                ? ApprovalEffectStatus.PENDING : ApprovalEffectStatus.valueOf(entity.getEffectStatus()));
        effect.setPayload(entity.getPayload());
        effect.setExecutedAt(entity.getExecutedAt());
        effect.setRetryCount(entity.getRetryCount() == null ? 0 : entity.getRetryCount());
        effect.setErrorMsg(entity.getErrorMsg());
        effect.setCreateTime(entity.getCreateTime());
        return effect;
    }
}
