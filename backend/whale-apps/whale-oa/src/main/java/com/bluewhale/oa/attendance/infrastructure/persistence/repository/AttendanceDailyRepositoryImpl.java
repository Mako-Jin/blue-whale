package com.bluewhale.oa.attendance.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.domain.model.AttendanceDaily;
import com.bluewhale.oa.attendance.domain.model.AttendanceDailyQuery;
import com.bluewhale.oa.attendance.domain.repository.AttendanceDailyRepository;
import com.bluewhale.oa.attendance.infrastructure.persistence.entity.AttendanceDailyEntity;
import com.bluewhale.oa.attendance.infrastructure.persistence.mapper.AttendanceDailyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 考勤日汇总仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AttendanceDailyRepositoryImpl implements AttendanceDailyRepository {

    private final AttendanceDailyMapper dailyMapper;

    @Override
    public Optional<AttendanceDaily> findByUserAndDate(Long userId, LocalDate workDate) {
        return Optional.ofNullable(dailyMapper.selectOne(Wrappers.<AttendanceDailyEntity>lambdaQuery()
                        .eq(AttendanceDailyEntity::getUserId, userId)
                        .eq(AttendanceDailyEntity::getWorkDate, workDate)))
                .map(AttendanceDailyRepositoryImpl::toModel);
    }

    @Override
    public void save(AttendanceDaily daily) {
        AttendanceDailyEntity entity = toEntity(daily);
        if (entity.getId() == null) {
            dailyMapper.insert(entity);
        } else {
            dailyMapper.updateById(entity);
        }
        daily.setId(entity.getId());
    }

    @Override
    public PageResult<AttendanceDaily> page(AttendanceDailyQuery query) {
        IPage<AttendanceDailyEntity> page = dailyMapper.selectPage(query.toPage(),
                Wrappers.<AttendanceDailyEntity>lambdaQuery()
                        .eq(query.getUserId() != null, AttendanceDailyEntity::getUserId, query.getUserId())
                        .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                                AttendanceDailyEntity::getStatus, query.getStatus())
                        .ge(query.getDateFrom() != null, AttendanceDailyEntity::getWorkDate, query.getDateFrom())
                        .le(query.getDateTo() != null, AttendanceDailyEntity::getWorkDate, query.getDateTo())
                        .orderByDesc(AttendanceDailyEntity::getWorkDate)
                        .orderByAsc(AttendanceDailyEntity::getUserId));
        List<AttendanceDaily> records = page.getRecords().stream()
                .map(AttendanceDailyRepositoryImpl::toModel).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<AttendanceDaily> listByUser(Long userId, LocalDate dateFrom, LocalDate dateTo) {
        return dailyMapper.selectList(Wrappers.<AttendanceDailyEntity>lambdaQuery()
                        .eq(AttendanceDailyEntity::getUserId, userId)
                        .ge(AttendanceDailyEntity::getWorkDate, dateFrom)
                        .le(AttendanceDailyEntity::getWorkDate, dateTo)
                        .orderByAsc(AttendanceDailyEntity::getWorkDate))
                .stream().map(AttendanceDailyRepositoryImpl::toModel).toList();
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 日汇总实体
     * @return 日汇总模型
     */
    private static AttendanceDaily toModel(AttendanceDailyEntity entity) {
        AttendanceDaily model = new AttendanceDaily();
        model.setId(entity.getId());
        model.setUserId(entity.getUserId());
        model.setUserName(entity.getUserName());
        model.setWorkDate(entity.getWorkDate());
        model.setGroupId(entity.getGroupId());
        model.setShiftCode(entity.getShiftCode());
        model.setShiftStart(entity.getShiftStart());
        model.setShiftEnd(entity.getShiftEnd());
        model.setFirstIn(entity.getFirstIn());
        model.setLastOut(entity.getLastOut());
        model.setLateMinutes(entity.getLateMinutes());
        model.setEarlyMinutes(entity.getEarlyMinutes());
        model.setAbsent(entity.getAbsent());
        model.setStatus(entity.getStatus());
        model.setRemark(entity.getRemark());
        return model;
    }

    /**
     * 领域模型转实体。
     *
     * @param model 日汇总模型
     * @return 日汇总实体
     */
    private static AttendanceDailyEntity toEntity(AttendanceDaily model) {
        AttendanceDailyEntity entity = new AttendanceDailyEntity();
        entity.setId(model.getId());
        entity.setUserId(model.getUserId());
        entity.setUserName(model.getUserName());
        entity.setWorkDate(model.getWorkDate());
        entity.setGroupId(model.getGroupId());
        entity.setShiftCode(model.getShiftCode());
        entity.setShiftStart(model.getShiftStart());
        entity.setShiftEnd(model.getShiftEnd());
        entity.setFirstIn(model.getFirstIn());
        entity.setLastOut(model.getLastOut());
        entity.setLateMinutes(model.getLateMinutes());
        entity.setEarlyMinutes(model.getEarlyMinutes());
        entity.setAbsent(model.getAbsent());
        entity.setStatus(model.getStatus());
        entity.setRemark(model.getRemark());
        return entity;
    }
}
