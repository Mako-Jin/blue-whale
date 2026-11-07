package com.bluewhale.oa.attendance.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.domain.model.AttendanceException;
import com.bluewhale.oa.attendance.domain.model.AttendanceExceptionQuery;
import com.bluewhale.oa.attendance.domain.repository.AttendanceExceptionRepository;
import com.bluewhale.oa.attendance.infrastructure.persistence.entity.AttendanceExceptionEntity;
import com.bluewhale.oa.attendance.infrastructure.persistence.mapper.AttendanceExceptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 考勤异常仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AttendanceExceptionRepositoryImpl implements AttendanceExceptionRepository {

    private final AttendanceExceptionMapper exceptionMapper;

    @Override
    public Optional<AttendanceException> find(Long userId, LocalDate workDate, String type) {
        return Optional.ofNullable(exceptionMapper.selectOne(Wrappers.<AttendanceExceptionEntity>lambdaQuery()
                        .eq(AttendanceExceptionEntity::getUserId, userId)
                        .eq(AttendanceExceptionEntity::getWorkDate, workDate)
                        .eq(AttendanceExceptionEntity::getType, type)))
                .map(AttendanceExceptionRepositoryImpl::toModel);
    }

    @Override
    public void save(AttendanceException exception) {
        AttendanceExceptionEntity entity = toEntity(exception);
        exceptionMapper.insert(entity);
        exception.setId(entity.getId());
    }

    @Override
    public void update(AttendanceException exception) {
        exceptionMapper.updateById(toEntity(exception));
    }

    @Override
    public Optional<AttendanceException> findById(Long id) {
        return Optional.ofNullable(exceptionMapper.selectById(id))
                .map(AttendanceExceptionRepositoryImpl::toModel);
    }

    @Override
    public PageResult<AttendanceException> page(AttendanceExceptionQuery query) {
        IPage<AttendanceExceptionEntity> page = exceptionMapper.selectPage(query.toPage(),
                Wrappers.<AttendanceExceptionEntity>lambdaQuery()
                        .eq(query.getUserId() != null, AttendanceExceptionEntity::getUserId, query.getUserId())
                        .eq(query.getType() != null && !query.getType().isBlank(),
                                AttendanceExceptionEntity::getType, query.getType())
                        .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                                AttendanceExceptionEntity::getStatus, query.getStatus())
                        .ge(query.getDateFrom() != null, AttendanceExceptionEntity::getWorkDate, query.getDateFrom())
                        .le(query.getDateTo() != null, AttendanceExceptionEntity::getWorkDate, query.getDateTo())
                        .orderByDesc(AttendanceExceptionEntity::getWorkDate)
                        .orderByAsc(AttendanceExceptionEntity::getUserId));
        List<AttendanceException> records = page.getRecords().stream()
                .map(AttendanceExceptionRepositoryImpl::toModel).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 考勤异常实体
     * @return 考勤异常模型
     */
    private static AttendanceException toModel(AttendanceExceptionEntity entity) {
        AttendanceException model = new AttendanceException();
        model.setId(entity.getId());
        model.setUserId(entity.getUserId());
        model.setUserName(entity.getUserName());
        model.setWorkDate(entity.getWorkDate());
        model.setType(entity.getType());
        model.setDescription(entity.getDescription());
        model.setStatus(entity.getStatus());
        model.setHandler(entity.getHandler());
        model.setHandledTime(entity.getHandledTime());
        model.setRemark(entity.getRemark());
        return model;
    }

    /**
     * 领域模型转实体。
     *
     * @param model 考勤异常模型
     * @return 考勤异常实体
     */
    private static AttendanceExceptionEntity toEntity(AttendanceException model) {
        AttendanceExceptionEntity entity = new AttendanceExceptionEntity();
        entity.setId(model.getId());
        entity.setUserId(model.getUserId());
        entity.setUserName(model.getUserName());
        entity.setWorkDate(model.getWorkDate());
        entity.setType(model.getType());
        entity.setDescription(model.getDescription());
        entity.setStatus(model.getStatus());
        entity.setHandler(model.getHandler());
        entity.setHandledTime(model.getHandledTime());
        entity.setRemark(model.getRemark());
        return entity;
    }
}
