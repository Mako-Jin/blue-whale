package com.bluewhale.oa.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceAction;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceLog;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceLogQuery;
import com.bluewhale.oa.hr.domain.repository.LeaveBalanceLogRepository;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.LeaveBalanceLogEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.LeaveBalanceLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 假期额度流水仓储实现（MyBatis-Plus，只追加）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class LeaveBalanceLogRepositoryImpl implements LeaveBalanceLogRepository {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final LeaveBalanceLogMapper leaveBalanceLogMapper;

    @Override
    public List<LeaveBalanceLog> page(LeaveBalanceLogQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        Page<LeaveBalanceLogEntity> page = leaveBalanceLogMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), size, false), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(LeaveBalanceLogQuery query) {
        Long total = leaveBalanceLogMapper.selectCount(filter(query));
        return total == null ? 0L : total;
    }

    @Override
    public void insert(LeaveBalanceLog log) {
        LeaveBalanceLogEntity entity = new LeaveBalanceLogEntity();
        fill(entity, log);
        leaveBalanceLogMapper.insert(entity);
        log.setId(entity.getId());
        log.setCreateTime(entity.getCreateTime());
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<LeaveBalanceLogEntity> filter(LeaveBalanceLogQuery query) {
        return new LambdaQueryWrapper<LeaveBalanceLogEntity>()
                .eq(query.getBalanceId() != null,
                        LeaveBalanceLogEntity::getBalanceId, query.getBalanceId())
                .eq(query.getUserId() != null, LeaveBalanceLogEntity::getUserId, query.getUserId())
                .eq(query.getLeaveTypeId() != null,
                        LeaveBalanceLogEntity::getLeaveTypeId, query.getLeaveTypeId())
                .eq(query.getBalanceYear() != null,
                        LeaveBalanceLogEntity::getBalanceYear, query.getBalanceYear())
                .eq(StringUtils.hasText(query.getAction()),
                        LeaveBalanceLogEntity::getAction, query.getAction())
                .orderByDesc(LeaveBalanceLogEntity::getCreateTime);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private LeaveBalanceLog toDomain(LeaveBalanceLogEntity entity) {
        LeaveBalanceLog log = new LeaveBalanceLog();
        log.setId(entity.getId());
        log.setBalanceId(entity.getBalanceId());
        log.setUserId(entity.getUserId());
        log.setLeaveTypeId(entity.getLeaveTypeId());
        log.setBalanceYear(entity.getBalanceYear());
        log.setAction(entity.getAction() == null ? null : LeaveBalanceAction.of(entity.getAction()));
        log.setDays(entity.getDays());
        log.setBeforeTotal(entity.getBeforeTotal());
        log.setAfterTotal(entity.getAfterTotal());
        log.setBeforeUsed(entity.getBeforeUsed());
        log.setAfterUsed(entity.getAfterUsed());
        log.setBeforeFrozen(entity.getBeforeFrozen());
        log.setAfterFrozen(entity.getAfterFrozen());
        log.setBizType(entity.getBizType());
        log.setBizId(entity.getBizId());
        log.setOperatorId(entity.getOperatorId());
        log.setRemark(entity.getRemark());
        log.setCreateTime(entity.getCreateTime());
        return log;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param log 领域模型
     */
    private void fill(LeaveBalanceLogEntity entity, LeaveBalanceLog log) {
        entity.setBalanceId(log.getBalanceId());
        entity.setUserId(log.getUserId());
        entity.setLeaveTypeId(log.getLeaveTypeId());
        entity.setBalanceYear(log.getBalanceYear());
        entity.setAction(log.getAction() == null ? null : log.getAction().name());
        entity.setDays(log.getDays());
        entity.setBeforeTotal(log.getBeforeTotal());
        entity.setAfterTotal(log.getAfterTotal());
        entity.setBeforeUsed(log.getBeforeUsed());
        entity.setAfterUsed(log.getAfterUsed());
        entity.setBeforeFrozen(log.getBeforeFrozen());
        entity.setAfterFrozen(log.getAfterFrozen());
        entity.setBizType(log.getBizType());
        entity.setBizId(log.getBizId());
        entity.setOperatorId(log.getOperatorId());
        entity.setRemark(log.getRemark());
    }
}
