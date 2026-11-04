package com.bluewhale.erp.masterdata.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.masterdata.domain.model.AccountingPeriod;
import com.bluewhale.erp.masterdata.domain.model.MasterDataQuery;
import com.bluewhale.erp.masterdata.domain.repository.AccountingPeriodRepository;
import com.bluewhale.erp.masterdata.infrastructure.persistence.entity.AccountingPeriodEntity;
import com.bluewhale.erp.masterdata.infrastructure.persistence.mapper.AccountingPeriodMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 会计期间仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AccountingPeriodRepositoryImpl implements AccountingPeriodRepository {

    private final AccountingPeriodMapper periodMapper;

    @Override
    public List<AccountingPeriod> page(MasterDataQuery query) {
        Page<AccountingPeriodEntity> page = periodMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(MasterDataQuery query) {
        return periodMapper.selectCount(filter(query));
    }

    @Override
    public Optional<AccountingPeriod> findById(Long id) {
        return Optional.ofNullable(periodMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<AccountingPeriod> findByCode(String periodCode) {
        return Optional.ofNullable(periodMapper.selectOne(new LambdaQueryWrapper<AccountingPeriodEntity>()
                .eq(AccountingPeriodEntity::getPeriodCode, periodCode))).map(this::toDomain);
    }

    @Override
    public void update(AccountingPeriod period) {
        AccountingPeriodEntity entity = new AccountingPeriodEntity();
        entity.setId(period.getId());
        entity.setStatus(period.getStatus());
        entity.setClosedAt(period.getClosedAt());
        entity.setClosedBy(period.getClosedBy());
        periodMapper.updateById(entity);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<AccountingPeriodEntity> filter(MasterDataQuery query) {
        LambdaQueryWrapper<AccountingPeriodEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(AccountingPeriodEntity::getPeriodCode, query.getKeyword());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AccountingPeriodEntity::getStatus, query.getStatus());
        }
        return wrapper.orderByAsc(AccountingPeriodEntity::getPeriodCode);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private AccountingPeriod toDomain(AccountingPeriodEntity entity) {
        AccountingPeriod period = new AccountingPeriod();
        period.setId(entity.getId());
        period.setPeriodCode(entity.getPeriodCode());
        period.setStartDate(entity.getStartDate());
        period.setEndDate(entity.getEndDate());
        period.setStatus(entity.getStatus());
        period.setClosedAt(entity.getClosedAt());
        period.setClosedBy(entity.getClosedBy());
        period.setRemark(entity.getRemark());
        return period;
    }
}
