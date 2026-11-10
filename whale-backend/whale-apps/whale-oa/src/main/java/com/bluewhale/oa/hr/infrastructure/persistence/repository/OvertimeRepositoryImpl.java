package com.bluewhale.oa.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.hr.domain.model.Overtime;
import com.bluewhale.oa.hr.domain.model.OvertimeCompensation;
import com.bluewhale.oa.hr.domain.model.OvertimeQuery;
import com.bluewhale.oa.hr.domain.model.OvertimeStatus;
import com.bluewhale.oa.hr.domain.repository.OvertimeRepository;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.OvertimeEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.OvertimeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 加班申请仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class OvertimeRepositoryImpl implements OvertimeRepository {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final OvertimeMapper overtimeMapper;

    @Override
    public List<Overtime> page(OvertimeQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        Page<OvertimeEntity> page = overtimeMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), size, false), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(OvertimeQuery query) {
        Long total = overtimeMapper.selectCount(filter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<Overtime> findById(Long id) {
        return Optional.ofNullable(overtimeMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String code, Long excludeId) {
        LambdaQueryWrapper<OvertimeEntity> wrapper = new LambdaQueryWrapper<OvertimeEntity>()
                .eq(OvertimeEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(OvertimeEntity::getId, excludeId);
        }
        Long total = overtimeMapper.selectCount(wrapper);
        return total != null && total > 0;
    }

    @Override
    public List<Overtime> listByApplicantAndDate(String applicant, LocalDate overtimeDate, Long excludeId) {
        LambdaQueryWrapper<OvertimeEntity> wrapper = new LambdaQueryWrapper<OvertimeEntity>()
                .eq(OvertimeEntity::getApplicant, applicant)
                .eq(OvertimeEntity::getOvertimeDate, overtimeDate)
                .notIn(OvertimeEntity::getStatus,
                        OvertimeStatus.REJECTED.name(), OvertimeStatus.CANCELLED.name());
        if (excludeId != null) {
            wrapper.ne(OvertimeEntity::getId, excludeId);
        }
        return overtimeMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public BigDecimal sumHours(String applicant, LocalDate dateFrom, LocalDate dateTo, Long excludeId) {
        LambdaQueryWrapper<OvertimeEntity> wrapper = new LambdaQueryWrapper<OvertimeEntity>()
                .eq(OvertimeEntity::getApplicant, applicant)
                .ge(OvertimeEntity::getOvertimeDate, dateFrom)
                .le(OvertimeEntity::getOvertimeDate, dateTo)
                .notIn(OvertimeEntity::getStatus,
                        OvertimeStatus.REJECTED.name(), OvertimeStatus.CANCELLED.name());
        if (excludeId != null) {
            wrapper.ne(OvertimeEntity::getId, excludeId);
        }
        return overtimeMapper.selectList(wrapper).stream()
                .map(OvertimeEntity::getHours)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public void insert(Overtime overtime) {
        OvertimeEntity entity = new OvertimeEntity();
        fill(entity, overtime);
        overtimeMapper.insert(entity);
        overtime.setId(entity.getId());
        overtime.setCreateTime(entity.getCreateTime());
        overtime.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Overtime overtime) {
        overtimeMapper.update(null, new LambdaUpdateWrapper<OvertimeEntity>()
                .eq(OvertimeEntity::getId, overtime.getId())
                .set(OvertimeEntity::getCode, overtime.getCode())
                .set(OvertimeEntity::getApplicant, overtime.getApplicant())
                .set(OvertimeEntity::getApplicantId, overtime.getApplicantId())
                .set(OvertimeEntity::getOvertimeDate, overtime.getOvertimeDate())
                .set(OvertimeEntity::getStartTime, overtime.getStartTime())
                .set(OvertimeEntity::getEndTime, overtime.getEndTime())
                .set(OvertimeEntity::getHours, overtime.getHours())
                .set(OvertimeEntity::getCompensation,
                        overtime.getCompensation() == null ? null : overtime.getCompensation().name())
                .set(OvertimeEntity::getProjectName, overtime.getProjectName())
                .set(OvertimeEntity::getReason, overtime.getReason())
                .set(OvertimeEntity::getStatus, overtime.getStatus() == null ? null : overtime.getStatus().name())
                .set(OvertimeEntity::getApprovalInstanceId, overtime.getApprovalInstanceId())
                .set(OvertimeEntity::getRemark, overtime.getRemark()));
    }

    @Override
    public void deleteById(Long id) {
        overtimeMapper.deleteById(id);
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<OvertimeEntity> filter(OvertimeQuery query) {
        return new LambdaQueryWrapper<OvertimeEntity>()
                .like(StringUtils.hasText(query.getCode()), OvertimeEntity::getCode, query.getCode())
                .eq(StringUtils.hasText(query.getStatus()), OvertimeEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getApplicant()),
                        OvertimeEntity::getApplicant, query.getApplicant())
                .eq(StringUtils.hasText(query.getCompensation()),
                        OvertimeEntity::getCompensation, query.getCompensation())
                .ge(query.getDateFrom() != null, OvertimeEntity::getOvertimeDate, query.getDateFrom())
                .le(query.getDateTo() != null, OvertimeEntity::getOvertimeDate, query.getDateTo())
                .orderByDesc(OvertimeEntity::getOvertimeDate)
                .orderByDesc(OvertimeEntity::getCreateTime);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Overtime toDomain(OvertimeEntity entity) {
        Overtime overtime = new Overtime();
        overtime.setId(entity.getId());
        overtime.setCode(entity.getCode());
        overtime.setApplicant(entity.getApplicant());
        overtime.setApplicantId(entity.getApplicantId());
        overtime.setOvertimeDate(entity.getOvertimeDate());
        overtime.setStartTime(entity.getStartTime());
        overtime.setEndTime(entity.getEndTime());
        overtime.setHours(entity.getHours());
        overtime.setCompensation(entity.getCompensation() == null
                ? null : OvertimeCompensation.of(entity.getCompensation()));
        overtime.setProjectName(entity.getProjectName());
        overtime.setReason(entity.getReason());
        overtime.setStatus(entity.getStatus() == null
                ? OvertimeStatus.PENDING : OvertimeStatus.of(entity.getStatus()));
        overtime.setApprovalInstanceId(entity.getApprovalInstanceId());
        overtime.setRemark(entity.getRemark());
        overtime.setCreateTime(entity.getCreateTime());
        overtime.setUpdateTime(entity.getUpdateTime());
        return overtime;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param overtime 领域模型
     */
    private void fill(OvertimeEntity entity, Overtime overtime) {
        entity.setCode(overtime.getCode());
        entity.setApplicant(overtime.getApplicant());
        entity.setApplicantId(overtime.getApplicantId());
        entity.setOvertimeDate(overtime.getOvertimeDate());
        entity.setStartTime(overtime.getStartTime());
        entity.setEndTime(overtime.getEndTime());
        entity.setHours(overtime.getHours());
        entity.setCompensation(overtime.getCompensation() == null
                ? OvertimeCompensation.COMP_TIME.name() : overtime.getCompensation().name());
        entity.setProjectName(overtime.getProjectName());
        entity.setReason(overtime.getReason());
        entity.setStatus(overtime.getStatus() == null
                ? OvertimeStatus.PENDING.name() : overtime.getStatus().name());
        entity.setApprovalInstanceId(overtime.getApprovalInstanceId());
        entity.setRemark(overtime.getRemark());
    }
}
