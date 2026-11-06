package com.bluewhale.oa.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.hr.domain.model.Leave;
import com.bluewhale.oa.hr.domain.model.LeaveQuery;
import com.bluewhale.oa.hr.domain.model.LeaveStatus;
import com.bluewhale.oa.hr.domain.repository.LeaveRepository;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.LeaveEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.LeaveMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 请假申请仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class LeaveRepositoryImpl implements LeaveRepository {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final LeaveMapper leaveMapper;

    @Override
    public List<Leave> page(LeaveQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        Page<LeaveEntity> page = leaveMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), size, false), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(LeaveQuery query) {
        Long total = leaveMapper.selectCount(filter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<Leave> findById(Long id) {
        return Optional.ofNullable(leaveMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String code, Long excludeId) {
        LambdaQueryWrapper<LeaveEntity> wrapper = new LambdaQueryWrapper<LeaveEntity>()
                .eq(LeaveEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(LeaveEntity::getId, excludeId);
        }
        Long total = leaveMapper.selectCount(wrapper);
        return total != null && total > 0;
    }

    @Override
    public List<Leave> listOverlapping(String applicant, String leaveType, LocalDate startDate,
                                       LocalDate endDate, Long excludeId) {
        LambdaQueryWrapper<LeaveEntity> wrapper = new LambdaQueryWrapper<LeaveEntity>()
                .eq(LeaveEntity::getApplicant, applicant)
                .eq(LeaveEntity::getLeaveType, leaveType)
                .le(LeaveEntity::getStartDate, endDate)
                .ge(LeaveEntity::getEndDate, startDate)
                .notIn(LeaveEntity::getStatus,
                        LeaveStatus.REJECTED.name(), LeaveStatus.CANCELLED.name());
        if (excludeId != null) {
            wrapper.ne(LeaveEntity::getId, excludeId);
        }
        return leaveMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Leave> listApprovedOverlapping(Collection<Long> applicantIds, LocalDate from,
                                               LocalDate to, int limit) {
        if (applicantIds == null || applicantIds.isEmpty() || from == null || to == null) {
            return List.of();
        }
        return leaveMapper.selectList(new LambdaQueryWrapper<LeaveEntity>()
                        .in(LeaveEntity::getApplicantId, applicantIds)
                        .eq(LeaveEntity::getStatus, LeaveStatus.APPROVED.name())
                        .le(LeaveEntity::getStartDate, to)
                        .ge(LeaveEntity::getEndDate, from)
                        .orderByAsc(LeaveEntity::getStartDate)
                        .last("LIMIT " + Math.min(Math.max(limit, 1), MAX_PAGE_SIZE)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(Leave leave) {
        LeaveEntity entity = new LeaveEntity();
        fill(entity, leave);
        leaveMapper.insert(entity);
        leave.setId(entity.getId());
        leave.setCreateTime(entity.getCreateTime());
        leave.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Leave leave) {
        leaveMapper.update(null, new LambdaUpdateWrapper<LeaveEntity>()
                .eq(LeaveEntity::getId, leave.getId())
                .set(LeaveEntity::getCode, leave.getCode())
                .set(LeaveEntity::getApplicant, leave.getApplicant())
                .set(LeaveEntity::getApplicantId, leave.getApplicantId())
                .set(LeaveEntity::getLeaveType, leave.getLeaveType())
                .set(LeaveEntity::getLeaveTypeId, leave.getLeaveTypeId())
                .set(LeaveEntity::getStartDate, leave.getStartDate())
                .set(LeaveEntity::getStartPeriod, leave.getStartPeriod())
                .set(LeaveEntity::getEndDate, leave.getEndDate())
                .set(LeaveEntity::getEndPeriod, leave.getEndPeriod())
                .set(LeaveEntity::getHandoverUserId, leave.getHandoverUserId())
                .set(LeaveEntity::getApprovalInstanceId, leave.getApprovalInstanceId())
                .set(LeaveEntity::getDays, leave.getDays())
                .set(LeaveEntity::getReason, leave.getReason())
                .set(LeaveEntity::getStatus, leave.getStatus() == null ? null : leave.getStatus().name())
                .set(LeaveEntity::getRemark, leave.getRemark()));
    }

    @Override
    public void deleteById(Long id) {
        leaveMapper.deleteById(id);
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<LeaveEntity> filter(LeaveQuery query) {
        return new LambdaQueryWrapper<LeaveEntity>()
                .like(StringUtils.hasText(query.getCode()), LeaveEntity::getCode, query.getCode())
                .eq(StringUtils.hasText(query.getStatus()), LeaveEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getApplicant()), LeaveEntity::getApplicant, query.getApplicant())
                .eq(StringUtils.hasText(query.getLeaveType()), LeaveEntity::getLeaveType, query.getLeaveType())
                .orderByDesc(LeaveEntity::getCreateTime);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Leave toDomain(LeaveEntity entity) {
        Leave leave = new Leave();
        leave.setId(entity.getId());
        leave.setCode(entity.getCode());
        leave.setApplicant(entity.getApplicant());
        leave.setApplicantId(entity.getApplicantId());
        leave.setLeaveType(entity.getLeaveType());
        leave.setLeaveTypeId(entity.getLeaveTypeId());
        leave.setStartDate(entity.getStartDate());
        leave.setStartPeriod(entity.getStartPeriod());
        leave.setEndDate(entity.getEndDate());
        leave.setEndPeriod(entity.getEndPeriod());
        leave.setHandoverUserId(entity.getHandoverUserId());
        leave.setApprovalInstanceId(entity.getApprovalInstanceId());
        leave.setDays(entity.getDays());
        leave.setReason(entity.getReason());
        leave.setStatus(entity.getStatus() == null ? LeaveStatus.PENDING : LeaveStatus.of(entity.getStatus()));
        leave.setRemark(entity.getRemark());
        leave.setCreateTime(entity.getCreateTime());
        leave.setUpdateTime(entity.getUpdateTime());
        return leave;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param leave 领域模型
     */
    private void fill(LeaveEntity entity, Leave leave) {
        entity.setCode(leave.getCode());
        entity.setApplicant(leave.getApplicant());
        entity.setApplicantId(leave.getApplicantId());
        entity.setLeaveType(leave.getLeaveType());
        entity.setLeaveTypeId(leave.getLeaveTypeId());
        entity.setStartDate(leave.getStartDate());
        entity.setStartPeriod(leave.getStartPeriod());
        entity.setEndDate(leave.getEndDate());
        entity.setEndPeriod(leave.getEndPeriod());
        entity.setHandoverUserId(leave.getHandoverUserId());
        entity.setApprovalInstanceId(leave.getApprovalInstanceId());
        entity.setDays(leave.getDays());
        entity.setReason(leave.getReason());
        entity.setStatus(leave.getStatus() == null ? LeaveStatus.PENDING.name() : leave.getStatus().name());
        entity.setRemark(leave.getRemark());
    }
}
