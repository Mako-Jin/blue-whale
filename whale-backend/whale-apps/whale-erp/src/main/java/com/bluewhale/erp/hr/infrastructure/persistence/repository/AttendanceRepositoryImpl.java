package com.bluewhale.erp.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.hr.domain.model.AttendanceSummary;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.repository.AttendanceRepository;
import com.bluewhale.erp.hr.infrastructure.persistence.entity.AttendanceEntity;
import com.bluewhale.erp.hr.infrastructure.persistence.mapper.AttendanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 考勤汇总仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AttendanceRepositoryImpl implements AttendanceRepository {

    private final AttendanceMapper attendanceMapper;

    @Override
    public List<AttendanceSummary> page(HrQuery query) {
        Page<AttendanceEntity> page = attendanceMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(HrQuery query) {
        return attendanceMapper.selectCount(filter(query));
    }

    @Override
    public Optional<AttendanceSummary> find(String employeeNo, String period) {
        AttendanceEntity entity = attendanceMapper.selectOne(new LambdaQueryWrapper<AttendanceEntity>()
                .eq(AttendanceEntity::getEmployeeNo, employeeNo)
                .eq(AttendanceEntity::getPeriod, period)
                .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public List<AttendanceSummary> listByPeriod(String period) {
        return attendanceMapper.selectList(new LambdaQueryWrapper<AttendanceEntity>()
                        .eq(AttendanceEntity::getPeriod, period))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(AttendanceSummary attendance) {
        AttendanceEntity entity = new AttendanceEntity();
        fill(entity, attendance);
        attendanceMapper.insert(entity);
        attendance.setId(entity.getId());
    }

    @Override
    public void update(AttendanceSummary attendance) {
        AttendanceEntity entity = new AttendanceEntity();
        entity.setId(attendance.getId());
        entity.setWorkDays(attendance.getWorkDays());
        entity.setActualDays(attendance.getActualDays());
        entity.setLeaveHours(attendance.getLeaveHours());
        entity.setOvertimeHours(attendance.getOvertimeHours());
        entity.setAbsentDays(attendance.getAbsentDays());
        entity.setRemark(attendance.getRemark());
        attendanceMapper.updateById(entity);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<AttendanceEntity> filter(HrQuery query) {
        LambdaQueryWrapper<AttendanceEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(AttendanceEntity::getEmployeeNo, query.getKeyword());
        }
        if (StringUtils.hasText(query.getEmployeeNo())) {
            wrapper.eq(AttendanceEntity::getEmployeeNo, query.getEmployeeNo());
        }
        if (StringUtils.hasText(query.getPeriod())) {
            wrapper.eq(AttendanceEntity::getPeriod, query.getPeriod());
        }
        return wrapper.orderByDesc(AttendanceEntity::getPeriod)
                .orderByAsc(AttendanceEntity::getEmployeeNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private AttendanceSummary toDomain(AttendanceEntity entity) {
        AttendanceSummary attendance = new AttendanceSummary();
        attendance.setId(entity.getId());
        attendance.setEmployeeNo(entity.getEmployeeNo());
        attendance.setPeriod(entity.getPeriod());
        attendance.setWorkDays(entity.getWorkDays());
        attendance.setActualDays(entity.getActualDays());
        attendance.setLeaveHours(entity.getLeaveHours());
        attendance.setOvertimeHours(entity.getOvertimeHours());
        attendance.setAbsentDays(entity.getAbsentDays());
        attendance.setRemark(entity.getRemark());
        return attendance;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param attendance 领域模型
     */
    private void fill(AttendanceEntity entity, AttendanceSummary attendance) {
        entity.setEmployeeNo(attendance.getEmployeeNo());
        entity.setPeriod(attendance.getPeriod());
        entity.setWorkDays(attendance.getWorkDays());
        entity.setActualDays(attendance.getActualDays());
        entity.setLeaveHours(attendance.getLeaveHours());
        entity.setOvertimeHours(attendance.getOvertimeHours());
        entity.setAbsentDays(attendance.getAbsentDays());
        entity.setRemark(attendance.getRemark());
    }
}
