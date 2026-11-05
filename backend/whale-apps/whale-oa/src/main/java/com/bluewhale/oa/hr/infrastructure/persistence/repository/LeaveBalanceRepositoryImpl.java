package com.bluewhale.oa.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.hr.domain.model.LeaveBalance;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceQuery;
import com.bluewhale.oa.hr.domain.repository.LeaveBalanceRepository;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.LeaveBalanceEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.LeaveBalanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 年度假期额度仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class LeaveBalanceRepositoryImpl implements LeaveBalanceRepository {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final LeaveBalanceMapper leaveBalanceMapper;

    @Override
    public List<LeaveBalance> page(LeaveBalanceQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        Page<LeaveBalanceEntity> page = leaveBalanceMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), size, false), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(LeaveBalanceQuery query) {
        Long total = leaveBalanceMapper.selectCount(filter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<LeaveBalance> findById(Long id) {
        return Optional.ofNullable(leaveBalanceMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<LeaveBalance> findByUserAndTypeAndYear(Long userId, Long leaveTypeId, Integer balanceYear) {
        return Optional.ofNullable(leaveBalanceMapper.selectOne(new LambdaQueryWrapper<LeaveBalanceEntity>()
                .eq(LeaveBalanceEntity::getUserId, userId)
                .eq(LeaveBalanceEntity::getLeaveTypeId, leaveTypeId)
                .eq(LeaveBalanceEntity::getBalanceYear, balanceYear))).map(this::toDomain);
    }

    @Override
    public List<LeaveBalance> listByUserAndYear(Long userId, Integer balanceYear) {
        return leaveBalanceMapper.selectList(new LambdaQueryWrapper<LeaveBalanceEntity>()
                .eq(LeaveBalanceEntity::getUserId, userId)
                .eq(LeaveBalanceEntity::getBalanceYear, balanceYear)
                .orderByAsc(LeaveBalanceEntity::getLeaveTypeId)).stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(LeaveBalance balance) {
        LeaveBalanceEntity entity = new LeaveBalanceEntity();
        fill(entity, balance);
        leaveBalanceMapper.insert(entity);
        balance.setId(entity.getId());
        balance.setCreateTime(entity.getCreateTime());
        balance.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(LeaveBalance balance) {
        leaveBalanceMapper.update(null, new LambdaUpdateWrapper<LeaveBalanceEntity>()
                .eq(LeaveBalanceEntity::getId, balance.getId())
                .set(LeaveBalanceEntity::getUserId, balance.getUserId())
                .set(LeaveBalanceEntity::getLeaveTypeId, balance.getLeaveTypeId())
                .set(LeaveBalanceEntity::getBalanceYear, balance.getBalanceYear())
                .set(LeaveBalanceEntity::getTotalDays, balance.getTotalDays())
                .set(LeaveBalanceEntity::getUsedDays, balance.getUsedDays())
                .set(LeaveBalanceEntity::getFrozenDays, balance.getFrozenDays())
                .set(LeaveBalanceEntity::getRemark, balance.getRemark()));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<LeaveBalanceEntity> filter(LeaveBalanceQuery query) {
        return new LambdaQueryWrapper<LeaveBalanceEntity>()
                .eq(query.getUserId() != null, LeaveBalanceEntity::getUserId, query.getUserId())
                .eq(query.getLeaveTypeId() != null, LeaveBalanceEntity::getLeaveTypeId, query.getLeaveTypeId())
                .eq(query.getBalanceYear() != null, LeaveBalanceEntity::getBalanceYear, query.getBalanceYear())
                .orderByDesc(LeaveBalanceEntity::getBalanceYear)
                .orderByAsc(LeaveBalanceEntity::getLeaveTypeId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private LeaveBalance toDomain(LeaveBalanceEntity entity) {
        LeaveBalance balance = new LeaveBalance();
        balance.setId(entity.getId());
        balance.setUserId(entity.getUserId());
        balance.setLeaveTypeId(entity.getLeaveTypeId());
        balance.setBalanceYear(entity.getBalanceYear());
        balance.setTotalDays(entity.getTotalDays());
        balance.setUsedDays(entity.getUsedDays());
        balance.setFrozenDays(entity.getFrozenDays());
        balance.setRemark(entity.getRemark());
        balance.setCreateTime(entity.getCreateTime());
        balance.setUpdateTime(entity.getUpdateTime());
        return balance;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param balance 领域模型
     */
    private void fill(LeaveBalanceEntity entity, LeaveBalance balance) {
        entity.setUserId(balance.getUserId());
        entity.setLeaveTypeId(balance.getLeaveTypeId());
        entity.setBalanceYear(balance.getBalanceYear());
        entity.setTotalDays(balance.getTotalDays());
        entity.setUsedDays(balance.getUsedDays());
        entity.setFrozenDays(balance.getFrozenDays());
        entity.setRemark(balance.getRemark());
    }
}
