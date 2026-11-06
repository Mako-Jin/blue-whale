package com.bluewhale.lms.credit.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.credit.domain.model.CreditLedger;
import com.bluewhale.lms.credit.domain.model.CreditLedgerQuery;
import com.bluewhale.lms.credit.domain.model.CreditSource;
import com.bluewhale.lms.credit.domain.repository.CreditLedgerRepository;
import com.bluewhale.lms.credit.infrastructure.persistence.entity.CreditLedgerEntity;
import com.bluewhale.lms.credit.infrastructure.persistence.mapper.CreditLedgerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 学分台账仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CreditLedgerRepositoryImpl implements CreditLedgerRepository {

    private final CreditLedgerMapper creditLedgerMapper;

    @Override
    public List<CreditLedger> page(CreditLedgerQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<CreditLedgerEntity> page = creditLedgerMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(CreditLedgerQuery query) {
        return creditLedgerMapper.selectCount(filter(query));
    }

    @Override
    public Optional<CreditLedger> findBySource(Long userId, CreditSource sourceType, Long sourceId) {
        return Optional.ofNullable(creditLedgerMapper.selectOne(new LambdaQueryWrapper<CreditLedgerEntity>()
                        .eq(CreditLedgerEntity::getUserId, userId)
                        .eq(CreditLedgerEntity::getSourceType, sourceType.name())
                        .eq(CreditLedgerEntity::getSourceId, sourceId)))
                .map(this::toDomain);
    }

    @Override
    public List<CreditLedger> listByUser(Long userId, Integer periodYear) {
        LambdaQueryWrapper<CreditLedgerEntity> wrapper = new LambdaQueryWrapper<CreditLedgerEntity>()
                .eq(CreditLedgerEntity::getUserId, userId);
        if (periodYear != null) {
            wrapper.eq(CreditLedgerEntity::getPeriodYear, periodYear);
        }
        wrapper.orderByDesc(CreditLedgerEntity::getOccurredAt);
        return creditLedgerMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public Long save(CreditLedger ledger) {
        CreditLedgerEntity entity = toEntity(ledger);
        if (ledger.getId() == null) {
            creditLedgerMapper.insert(entity);
        } else {
            creditLedgerMapper.updateById(entity);
        }
        ledger.setId(entity.getId());
        ledger.setCreateTime(entity.getCreateTime());
        ledger.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<CreditLedgerEntity> filter(CreditLedgerQuery query) {
        LambdaQueryWrapper<CreditLedgerEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(CreditLedgerEntity::getUsername, query.getKeyword().trim());
        }
        if (query.getUserId() != null) {
            wrapper.eq(CreditLedgerEntity::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(CreditLedgerEntity::getSourceType, query.getSourceType().trim().toUpperCase());
        }
        if (query.getPeriodYear() != null) {
            wrapper.eq(CreditLedgerEntity::getPeriodYear, query.getPeriodYear());
        }
        wrapper.orderByDesc(CreditLedgerEntity::getOccurredAt);
        return wrapper;
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 台账记录
     */
    private CreditLedger toDomain(CreditLedgerEntity entity) {
        CreditLedger ledger = new CreditLedger();
        ledger.setId(entity.getId());
        ledger.setUserId(entity.getUserId());
        ledger.setUsername(entity.getUsername());
        ledger.setSourceType(CreditSource.parse(entity.getSourceType()));
        ledger.setSourceId(entity.getSourceId());
        ledger.setRuleId(entity.getRuleId());
        ledger.setRuleCode(entity.getRuleCode());
        ledger.setCredit(entity.getCredit());
        ledger.setOccurredAt(entity.getOccurredAt());
        ledger.setPeriodYear(entity.getPeriodYear());
        ledger.setRecordedBy(entity.getRecordedBy());
        ledger.setRemark(entity.getRemark());
        ledger.setCreateTime(entity.getCreateTime());
        ledger.setUpdateTime(entity.getUpdateTime());
        return ledger;
    }

    /**
     * 领域转实体。
     *
     * @param ledger 台账记录
     * @return 实体
     */
    private CreditLedgerEntity toEntity(CreditLedger ledger) {
        CreditLedgerEntity entity = new CreditLedgerEntity();
        entity.setId(ledger.getId());
        entity.setUserId(ledger.getUserId());
        entity.setUsername(ledger.getUsername());
        entity.setSourceType(ledger.getSourceType() == null ? null : ledger.getSourceType().name());
        entity.setSourceId(ledger.getSourceId());
        entity.setRuleId(ledger.getRuleId());
        entity.setRuleCode(ledger.getRuleCode());
        entity.setCredit(ledger.getCredit());
        entity.setOccurredAt(ledger.getOccurredAt());
        entity.setPeriodYear(ledger.getPeriodYear());
        entity.setRecordedBy(ledger.getRecordedBy());
        entity.setRemark(ledger.getRemark());
        entity.setCreateTime(ledger.getCreateTime());
        entity.setUpdateTime(ledger.getUpdateTime());
        return entity;
    }
}
