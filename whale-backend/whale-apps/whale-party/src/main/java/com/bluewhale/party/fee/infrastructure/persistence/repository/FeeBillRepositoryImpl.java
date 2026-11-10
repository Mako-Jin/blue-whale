package com.bluewhale.party.fee.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.party.fee.domain.model.FeeBill;
import com.bluewhale.party.fee.domain.model.FeeBillQuery;
import com.bluewhale.party.fee.domain.model.FeeBillStatus;
import com.bluewhale.party.fee.domain.repository.FeeBillRepository;
import com.bluewhale.party.fee.infrastructure.persistence.entity.FeeBillEntity;
import com.bluewhale.party.fee.infrastructure.persistence.mapper.FeeBillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 党费账单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class FeeBillRepositoryImpl implements FeeBillRepository {

    private final FeeBillMapper feeBillMapper;

    @Override
    public List<FeeBill> page(FeeBillQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<FeeBillEntity> page = feeBillMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(FeeBillQuery query) {
        return feeBillMapper.selectCount(filter(query));
    }

    @Override
    public Optional<FeeBill> findById(Long id) {
        return Optional.ofNullable(feeBillMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<FeeBill> findByMemberAndPeriod(Long memberId, String periodMonth) {
        return Optional.ofNullable(feeBillMapper.selectOne(new LambdaQueryWrapper<FeeBillEntity>()
                .eq(FeeBillEntity::getMemberId, memberId)
                .eq(FeeBillEntity::getPeriodMonth, periodMonth))).map(this::toDomain);
    }

    @Override
    public long countByPeriod(String periodMonth) {
        return feeBillMapper.selectCount(new LambdaQueryWrapper<FeeBillEntity>()
                .eq(FeeBillEntity::getPeriodMonth, periodMonth));
    }

    @Override
    public BigDecimal sumDueByPeriod(String periodMonth) {
        BigDecimal sum = feeBillMapper.sumDueByPeriod(periodMonth);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public BigDecimal sumPaidByPeriod(String periodMonth) {
        BigDecimal sum = feeBillMapper.sumPaidByPeriod(periodMonth);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public List<FeeBill> listByPeriod(String periodMonth, Long branchId) {
        LambdaQueryWrapper<FeeBillEntity> wrapper = new LambdaQueryWrapper<FeeBillEntity>()
                .eq(FeeBillEntity::getPeriodMonth, periodMonth);
        if (branchId != null) {
            wrapper.eq(FeeBillEntity::getBranchId, branchId);
        }
        return feeBillMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public Long save(FeeBill bill) {
        FeeBillEntity entity = toEntity(bill);
        if (entity.getId() == null) {
            feeBillMapper.insert(entity);
        } else {
            feeBillMapper.updateById(entity);
        }
        bill.setId(entity.getId());
        bill.setCreateTime(entity.getCreateTime());
        bill.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<FeeBillEntity> filter(FeeBillQuery query) {
        LambdaQueryWrapper<FeeBillEntity> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(FeeBillEntity::getPeriodMonth).orderByAsc(FeeBillEntity::getMemberId);
        }
        if (StringUtils.hasText(query.getPeriodMonth())) {
            wrapper.eq(FeeBillEntity::getPeriodMonth, query.getPeriodMonth().trim());
        }
        if (query.getBranchId() != null) {
            wrapper.eq(FeeBillEntity::getBranchId, query.getBranchId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(FeeBillEntity::getStatus, query.getStatus().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (Boolean.TRUE.equals(query.getOutstanding())) {
            wrapper.in(FeeBillEntity::getStatus,
                    FeeBillStatus.UNPAID.name(), FeeBillStatus.PARTIAL.name());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(FeeBillEntity::getMemberName, query.getKeyword().trim());
        }
        return wrapper.orderByDesc(FeeBillEntity::getPeriodMonth)
                .orderByAsc(FeeBillEntity::getBranchId)
                .orderByAsc(FeeBillEntity::getMemberId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private FeeBill toDomain(FeeBillEntity entity) {
        FeeBill bill = new FeeBill();
        bill.setId(entity.getId());
        bill.setMemberId(entity.getMemberId());
        bill.setBranchId(entity.getBranchId());
        bill.setMemberName(entity.getMemberName());
        bill.setPeriodMonth(entity.getPeriodMonth());
        bill.setDuesBase(entity.getDuesBase());
        bill.setRate(entity.getRate());
        bill.setDueAmount(entity.getDueAmount());
        bill.setPaidAmount(entity.getPaidAmount());
        bill.setPaidDate(entity.getPaidDate());
        bill.setStatus(FeeBillStatus.parse(entity.getStatus()));
        bill.setRemark(entity.getRemark());
        bill.setCreateTime(entity.getCreateTime());
        bill.setUpdateTime(entity.getUpdateTime());
        return bill;
    }

    /**
     * 领域模型转实体。
     *
     * @param bill 领域模型
     * @return 实体
     */
    private FeeBillEntity toEntity(FeeBill bill) {
        FeeBillEntity entity = new FeeBillEntity();
        entity.setId(bill.getId());
        entity.setMemberId(bill.getMemberId());
        entity.setBranchId(bill.getBranchId());
        entity.setMemberName(bill.getMemberName());
        entity.setPeriodMonth(bill.getPeriodMonth());
        entity.setDuesBase(bill.getDuesBase());
        entity.setRate(bill.getRate());
        entity.setDueAmount(bill.getDueAmount());
        entity.setPaidAmount(bill.getPaidAmount());
        entity.setPaidDate(bill.getPaidDate());
        entity.setStatus(bill.getStatus() == null ? FeeBillStatus.UNPAID.name() : bill.getStatus().name());
        entity.setRemark(bill.getRemark());
        return entity;
    }
}
