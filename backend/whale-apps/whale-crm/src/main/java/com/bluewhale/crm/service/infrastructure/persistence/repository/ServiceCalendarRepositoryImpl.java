package com.bluewhale.crm.service.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.service.domain.model.ServiceCalendar;
import com.bluewhale.crm.service.domain.repository.ServiceCalendarRepository;
import com.bluewhale.crm.service.infrastructure.persistence.entity.ServiceCalendarEntity;
import com.bluewhale.crm.service.infrastructure.persistence.mapper.ServiceCalendarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 工作日历仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ServiceCalendarRepositoryImpl implements ServiceCalendarRepository {

    private final ServiceCalendarMapper serviceCalendarMapper;

    @Override
    public ServiceCalendar findEnabled(String code) {
        ServiceCalendarEntity entity = serviceCalendarMapper.selectOne(new LambdaQueryWrapper<ServiceCalendarEntity>()
                .eq(ServiceCalendarEntity::getCalendarCode, code)
                .eq(ServiceCalendarEntity::getEnabled, true)
                .last("limit 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void upsert(ServiceCalendar calendar) {
        ServiceCalendarEntity existing = serviceCalendarMapper.selectOne(
                new LambdaQueryWrapper<ServiceCalendarEntity>()
                        .eq(ServiceCalendarEntity::getCalendarCode, calendar.getCalendarCode())
                        .last("limit 1"));
        ServiceCalendarEntity entity = toEntity(calendar);
        if (existing == null) {
            serviceCalendarMapper.insert(entity);
        } else {
            entity.setId(existing.getId());
            serviceCalendarMapper.updateById(entity);
        }
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 日历
     */
    private ServiceCalendar toDomain(ServiceCalendarEntity entity) {
        ServiceCalendar calendar = new ServiceCalendar();
        calendar.setId(entity.getId());
        calendar.setTenantId(entity.getTenantId());
        calendar.setCalendarCode(entity.getCalendarCode());
        calendar.setWorkdayStart(entity.getWorkdayStart());
        calendar.setWorkdayEnd(entity.getWorkdayEnd());
        calendar.setBreakMinutes(entity.getBreakMinutes());
        calendar.setWeekdays(entity.getWeekdays());
        calendar.setEnabled(entity.getEnabled());
        return calendar;
    }

    /**
     * 领域对象转实体。
     *
     * @param calendar 日历
     * @return 实体
     */
    private ServiceCalendarEntity toEntity(ServiceCalendar calendar) {
        ServiceCalendarEntity entity = new ServiceCalendarEntity();
        entity.setTenantId(calendar.getTenantId());
        entity.setCalendarCode(calendar.getCalendarCode());
        entity.setWorkdayStart(calendar.getWorkdayStart());
        entity.setWorkdayEnd(calendar.getWorkdayEnd());
        entity.setBreakMinutes(calendar.getBreakMinutes());
        entity.setWeekdays(calendar.getWeekdays());
        entity.setEnabled(calendar.getEnabled());
        return entity;
    }
}
