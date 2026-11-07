package com.bluewhale.oa.attendance.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.domain.model.AttendanceCorrection;
import com.bluewhale.oa.attendance.domain.model.AttendanceCorrectionQuery;
import com.bluewhale.oa.attendance.domain.model.AttendanceStatus;
import com.bluewhale.oa.attendance.domain.repository.AttendanceCorrectionRepository;
import com.bluewhale.oa.attendance.infrastructure.persistence.entity.AttendanceCorrectionEntity;
import com.bluewhale.oa.attendance.infrastructure.persistence.mapper.AttendanceCorrectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 补卡申请仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AttendanceCorrectionRepositoryImpl implements AttendanceCorrectionRepository {

    private final AttendanceCorrectionMapper correctionMapper;

    @Override
    public void save(AttendanceCorrection correction) {
        AttendanceCorrectionEntity entity = toEntity(correction);
        if (entity.getId() == null) {
            correctionMapper.insert(entity);
        } else {
            correctionMapper.updateById(entity);
        }
        correction.setId(entity.getId());
    }

    @Override
    public Optional<AttendanceCorrection> findById(Long id) {
        return Optional.ofNullable(correctionMapper.selectById(id))
                .map(AttendanceCorrectionRepositoryImpl::toModel);
    }

    @Override
    public Optional<AttendanceCorrection> findByCode(String code) {
        return Optional.ofNullable(correctionMapper.selectOne(Wrappers.<AttendanceCorrectionEntity>lambdaQuery()
                        .eq(AttendanceCorrectionEntity::getCode, code)))
                .map(AttendanceCorrectionRepositoryImpl::toModel);
    }

    @Override
    public Optional<AttendanceCorrection> findPending(Long userId, LocalDate workDate, String punchType) {
        return Optional.ofNullable(correctionMapper.selectOne(Wrappers.<AttendanceCorrectionEntity>lambdaQuery()
                        .eq(AttendanceCorrectionEntity::getUserId, userId)
                        .eq(AttendanceCorrectionEntity::getWorkDate, workDate)
                        .eq(AttendanceCorrectionEntity::getPunchType, punchType)
                        .eq(AttendanceCorrectionEntity::getStatus, AttendanceStatus.CORRECTION_PENDING)
                        .last("LIMIT 1")))
                .map(AttendanceCorrectionRepositoryImpl::toModel);
    }

    @Override
    public long countByMonth(Long userId, LocalDate monthStart, LocalDate monthEnd) {
        return correctionMapper.selectCount(Wrappers.<AttendanceCorrectionEntity>lambdaQuery()
                .eq(AttendanceCorrectionEntity::getUserId, userId)
                .ge(AttendanceCorrectionEntity::getWorkDate, monthStart)
                .le(AttendanceCorrectionEntity::getWorkDate, monthEnd)
                .in(AttendanceCorrectionEntity::getStatus,
                        AttendanceStatus.CORRECTION_PENDING, AttendanceStatus.CORRECTION_APPROVED));
    }

    @Override
    public PageResult<AttendanceCorrection> page(AttendanceCorrectionQuery query) {
        IPage<AttendanceCorrectionEntity> page = correctionMapper.selectPage(query.toPage(),
                Wrappers.<AttendanceCorrectionEntity>lambdaQuery()
                        .eq(query.getUserId() != null, AttendanceCorrectionEntity::getUserId, query.getUserId())
                        .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                                AttendanceCorrectionEntity::getStatus, query.getStatus())
                        .ge(query.getDateFrom() != null, AttendanceCorrectionEntity::getWorkDate, query.getDateFrom())
                        .le(query.getDateTo() != null, AttendanceCorrectionEntity::getWorkDate, query.getDateTo())
                        .orderByDesc(AttendanceCorrectionEntity::getWorkDate));
        List<AttendanceCorrection> records = page.getRecords().stream()
                .map(AttendanceCorrectionRepositoryImpl::toModel).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 补卡申请实体
     * @return 补卡申请模型
     */
    private static AttendanceCorrection toModel(AttendanceCorrectionEntity entity) {
        AttendanceCorrection model = new AttendanceCorrection();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setUserId(entity.getUserId());
        model.setUserName(entity.getUserName());
        model.setWorkDate(entity.getWorkDate());
        model.setPunchTime(entity.getPunchTime());
        model.setPunchType(entity.getPunchType());
        model.setReason(entity.getReason());
        model.setStatus(entity.getStatus());
        model.setApprovalInstanceId(entity.getApprovalInstanceId());
        model.setHandler(entity.getHandler());
        model.setHandledTime(entity.getHandledTime());
        model.setRemark(entity.getRemark());
        return model;
    }

    /**
     * 领域模型转实体。
     *
     * @param model 补卡申请模型
     * @return 补卡申请实体
     */
    private static AttendanceCorrectionEntity toEntity(AttendanceCorrection model) {
        AttendanceCorrectionEntity entity = new AttendanceCorrectionEntity();
        entity.setId(model.getId());
        entity.setCode(model.getCode());
        entity.setUserId(model.getUserId());
        entity.setUserName(model.getUserName());
        entity.setWorkDate(model.getWorkDate());
        entity.setPunchTime(model.getPunchTime());
        entity.setPunchType(model.getPunchType());
        entity.setReason(model.getReason());
        entity.setStatus(model.getStatus());
        entity.setApprovalInstanceId(model.getApprovalInstanceId());
        entity.setHandler(model.getHandler());
        entity.setHandledTime(model.getHandledTime());
        entity.setRemark(model.getRemark());
        return entity;
    }
}
