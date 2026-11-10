package com.bluewhale.oa.attendance.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecord;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecordQuery;
import com.bluewhale.oa.attendance.domain.repository.AttendanceRecordRepository;
import com.bluewhale.oa.attendance.infrastructure.persistence.entity.AttendanceRecordEntity;
import com.bluewhale.oa.attendance.infrastructure.persistence.mapper.AttendanceRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 原始打卡记录仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AttendanceRecordRepositoryImpl implements AttendanceRecordRepository {

    private final AttendanceRecordMapper recordMapper;

    @Override
    public boolean exists(Long userId, LocalDateTime punchTime, String punchType) {
        return recordMapper.selectCount(Wrappers.<AttendanceRecordEntity>lambdaQuery()
                .eq(AttendanceRecordEntity::getUserId, userId)
                .eq(AttendanceRecordEntity::getPunchTime, punchTime)
                .eq(AttendanceRecordEntity::getPunchType, punchType)) > 0;
    }

    @Override
    public void save(AttendanceRecord record) {
        AttendanceRecordEntity entity = toEntity(record);
        recordMapper.insert(entity);
        record.setId(entity.getId());
    }

    @Override
    public PageResult<AttendanceRecord> page(AttendanceRecordQuery query) {
        IPage<AttendanceRecordEntity> page = recordMapper.selectPage(query.toPage(),
                Wrappers.<AttendanceRecordEntity>lambdaQuery()
                        .eq(query.getUserId() != null, AttendanceRecordEntity::getUserId, query.getUserId())
                        .eq(query.getPunchType() != null && !query.getPunchType().isBlank(),
                                AttendanceRecordEntity::getPunchType, query.getPunchType())
                        .ge(query.getDateFrom() != null, AttendanceRecordEntity::getPunchTime,
                                query.getDateFrom() == null ? null : query.getDateFrom().atStartOfDay())
                        .le(query.getDateTo() != null, AttendanceRecordEntity::getPunchTime,
                                query.getDateTo() == null ? null : LocalDateTime.of(query.getDateTo(), LocalTime.MAX))
                        .orderByDesc(AttendanceRecordEntity::getPunchTime));
        List<AttendanceRecord> records = page.getRecords().stream()
                .map(AttendanceRecordRepositoryImpl::toModel).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<AttendanceRecord> listByUser(Long userId, LocalDate dateFrom, LocalDate dateTo) {
        return recordMapper.selectList(Wrappers.<AttendanceRecordEntity>lambdaQuery()
                        .eq(AttendanceRecordEntity::getUserId, userId)
                        .ge(AttendanceRecordEntity::getPunchTime, dateFrom.atStartOfDay())
                        .le(AttendanceRecordEntity::getPunchTime, LocalDateTime.of(dateTo, LocalTime.MAX))
                        .orderByAsc(AttendanceRecordEntity::getPunchTime))
                .stream().map(AttendanceRecordRepositoryImpl::toModel).toList();
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 打卡记录实体
     * @return 打卡记录模型
     */
    private static AttendanceRecord toModel(AttendanceRecordEntity entity) {
        AttendanceRecord model = new AttendanceRecord();
        model.setId(entity.getId());
        model.setUserId(entity.getUserId());
        model.setUserName(entity.getUserName());
        model.setPunchTime(entity.getPunchTime());
        model.setPunchType(entity.getPunchType());
        model.setSource(entity.getSource());
        model.setLatitude(entity.getLatitude());
        model.setLongitude(entity.getLongitude());
        model.setAddress(entity.getAddress());
        model.setDeviceCode(entity.getDeviceCode());
        model.setCorrection(entity.getCorrection());
        model.setRemark(entity.getRemark());
        return model;
    }

    /**
     * 领域模型转实体。
     *
     * @param model 打卡记录模型
     * @return 打卡记录实体
     */
    private static AttendanceRecordEntity toEntity(AttendanceRecord model) {
        AttendanceRecordEntity entity = new AttendanceRecordEntity();
        entity.setId(model.getId());
        entity.setUserId(model.getUserId());
        entity.setUserName(model.getUserName());
        entity.setPunchTime(model.getPunchTime());
        entity.setPunchType(model.getPunchType());
        entity.setSource(model.getSource());
        entity.setLatitude(model.getLatitude());
        entity.setLongitude(model.getLongitude());
        entity.setAddress(model.getAddress());
        entity.setDeviceCode(model.getDeviceCode());
        entity.setCorrection(model.getCorrection());
        entity.setRemark(model.getRemark());
        return entity;
    }
}
