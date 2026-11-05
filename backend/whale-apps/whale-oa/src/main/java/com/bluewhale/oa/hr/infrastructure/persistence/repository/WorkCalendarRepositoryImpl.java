package com.bluewhale.oa.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.hr.domain.model.WorkCalendarDay;
import com.bluewhale.oa.hr.domain.model.WorkCalendarQuery;
import com.bluewhale.oa.hr.domain.repository.WorkCalendarRepository;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.WorkCalendarEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.WorkCalendarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 工作日历仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class WorkCalendarRepositoryImpl implements WorkCalendarRepository {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final WorkCalendarMapper workCalendarMapper;

    @Override
    public List<WorkCalendarDay> page(WorkCalendarQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        Page<WorkCalendarEntity> page = workCalendarMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), size, false), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(WorkCalendarQuery query) {
        Long total = workCalendarMapper.selectCount(filter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<WorkCalendarDay> findByDate(LocalDate calendarDate) {
        return Optional.ofNullable(workCalendarMapper.selectOne(new LambdaQueryWrapper<WorkCalendarEntity>()
                .eq(WorkCalendarEntity::getCalendarDate, calendarDate))).map(this::toDomain);
    }

    @Override
    public List<WorkCalendarDay> listByDateRange(LocalDate startDate, LocalDate endDate) {
        return workCalendarMapper.selectList(new LambdaQueryWrapper<WorkCalendarEntity>()
                .ge(WorkCalendarEntity::getCalendarDate, startDate)
                .le(WorkCalendarEntity::getCalendarDate, endDate)
                .orderByAsc(WorkCalendarEntity::getCalendarDate)).stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(WorkCalendarDay day) {
        WorkCalendarEntity entity = new WorkCalendarEntity();
        fill(entity, day);
        workCalendarMapper.insert(entity);
        day.setCalendarDate(entity.getCalendarDate());
    }

    @Override
    public void updateByDate(WorkCalendarDay day) {
        workCalendarMapper.update(null, new LambdaUpdateWrapper<WorkCalendarEntity>()
                .eq(WorkCalendarEntity::getCalendarDate, day.getCalendarDate())
                .set(WorkCalendarEntity::getWorkday, day.getWorkday())
                .set(WorkCalendarEntity::getWorkHours, day.getWorkHours())
                .set(WorkCalendarEntity::getHolidayName, day.getHolidayName())
                .set(WorkCalendarEntity::getRemark, day.getRemark()));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<WorkCalendarEntity> filter(WorkCalendarQuery query) {
        return new LambdaQueryWrapper<WorkCalendarEntity>()
                .ge(query.getStartDate() != null, WorkCalendarEntity::getCalendarDate, query.getStartDate())
                .le(query.getEndDate() != null, WorkCalendarEntity::getCalendarDate, query.getEndDate())
                .eq(query.getWorkday() != null, WorkCalendarEntity::getWorkday, query.getWorkday())
                .orderByAsc(WorkCalendarEntity::getCalendarDate);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private WorkCalendarDay toDomain(WorkCalendarEntity entity) {
        WorkCalendarDay day = new WorkCalendarDay();
        day.setCalendarDate(entity.getCalendarDate());
        day.setWorkday(entity.getWorkday());
        day.setWorkHours(entity.getWorkHours());
        day.setHolidayName(entity.getHolidayName());
        day.setRemark(entity.getRemark());
        return day;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param day 领域模型
     */
    private void fill(WorkCalendarEntity entity, WorkCalendarDay day) {
        entity.setCalendarDate(day.getCalendarDate());
        entity.setWorkday(day.getWorkday());
        entity.setWorkHours(day.getWorkHours());
        entity.setHolidayName(day.getHolidayName());
        entity.setRemark(day.getRemark());
    }
}
