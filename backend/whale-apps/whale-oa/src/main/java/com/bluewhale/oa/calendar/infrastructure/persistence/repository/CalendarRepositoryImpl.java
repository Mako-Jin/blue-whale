package com.bluewhale.oa.calendar.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.calendar.domain.model.CalendarEntry;
import com.bluewhale.oa.calendar.domain.model.CalendarQuery;
import com.bluewhale.oa.calendar.domain.model.CalendarShare;
import com.bluewhale.oa.calendar.domain.repository.CalendarRepository;
import com.bluewhale.oa.calendar.infrastructure.persistence.entity.CalendarEntryEntity;
import com.bluewhale.oa.calendar.infrastructure.persistence.entity.CalendarShareEntity;
import com.bluewhale.oa.calendar.infrastructure.persistence.mapper.CalendarEntryMapper;
import com.bluewhale.oa.calendar.infrastructure.persistence.mapper.CalendarShareMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CalendarRepositoryImpl implements CalendarRepository {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 提醒候选时间上限（天） */
    private static final long REMIND_CANDIDATE_DAYS = 8L;

    private final CalendarEntryMapper entryMapper;

    private final CalendarShareMapper shareMapper;

    @Override
    public CalendarEntry findEntry(Long id) {
        CalendarEntryEntity entity = entryMapper.selectById(id);
        return entity == null ? null : toEntry(entity);
    }

    @Override
    public PageResult<CalendarEntry> pageEntries(CalendarQuery query) {
        LambdaQueryWrapper<CalendarEntryEntity> wrapper = entryFilter(query);
        Page<CalendarEntryEntity> page = entryMapper.selectPage(page(query), wrapper);
        List<CalendarEntry> records = page.getRecords().stream().map(CalendarRepositoryImpl::toEntry).toList();
        Long total = entryMapper.selectCount(wrapper);
        return PageResult.of(records, total == null ? 0L : total, query.getCurrent(), query.getSize());
    }

    @Override
    public List<CalendarShare> listShares(Long calendarId) {
        return shareMapper.selectList(new LambdaQueryWrapper<CalendarShareEntity>()
                        .eq(CalendarShareEntity::getCalendarId, calendarId)
                        .orderByAsc(CalendarShareEntity::getId))
                .stream().map(CalendarRepositoryImpl::toShare).toList();
    }

    @Override
    public boolean hasShare(Long calendarId, Long userId) {
        Long total = shareMapper.selectCount(new LambdaQueryWrapper<CalendarShareEntity>()
                .eq(CalendarShareEntity::getCalendarId, calendarId)
                .eq(CalendarShareEntity::getUserId, userId));
        return total != null && total > 0;
    }

    @Override
    public void saveEntry(CalendarEntry entry) {
        CalendarEntryEntity entity = new CalendarEntryEntity();
        fill(entity, entry);
        entryMapper.insert(entity);
        entry.setId(entity.getId());
        entry.setCreateTime(entity.getCreateTime());
        entry.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void updateEntry(CalendarEntry entry) {
        CalendarEntryEntity entity = new CalendarEntryEntity();
        entity.setId(entry.getId());
        fill(entity, entry);
        entryMapper.updateById(entity);
    }

    @Override
    public void deleteEntry(Long id) {
        entryMapper.deleteById(id);
    }

    @Override
    public void deleteShares(Long calendarId) {
        shareMapper.delete(new LambdaQueryWrapper<CalendarShareEntity>()
                .eq(CalendarShareEntity::getCalendarId, calendarId));
    }

    @Override
    public void saveShare(CalendarShare share) {
        CalendarShareEntity entity = new CalendarShareEntity();
        entity.setCalendarId(share.getCalendarId());
        entity.setUserId(share.getUserId());
        entity.setUserName(share.getUserName());
        shareMapper.insert(entity);
        share.setId(entity.getId());
    }

    @Override
    public List<CalendarEntry> listReminders(Long ownerId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime candidateEnd = to.plusDays(REMIND_CANDIDATE_DAYS);
        return entryMapper.selectList(new LambdaQueryWrapper<CalendarEntryEntity>()
                        .eq(CalendarEntryEntity::getOwnerId, ownerId)
                        .isNotNull(CalendarEntryEntity::getRemindMinutes)
                        .gt(CalendarEntryEntity::getStartTime, from)
                        .le(CalendarEntryEntity::getStartTime, candidateEnd)
                        .orderByAsc(CalendarEntryEntity::getStartTime))
                .stream()
                .filter(entity -> entity.getRemindMinutes() != null && entity.getStartTime() != null)
                .filter(entity -> {
                    LocalDateTime remindTime = entity.getStartTime().minusMinutes(entity.getRemindMinutes());
                    return !remindTime.isBefore(from) && !remindTime.isAfter(to);
                })
                .map(CalendarRepositoryImpl::toEntry)
                .toList();
    }

    private Page<CalendarEntryEntity> page(CalendarQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        return new Page<>(Math.max(query.getCurrent(), 1L), size, false);
    }

    private LambdaQueryWrapper<CalendarEntryEntity> entryFilter(CalendarQuery query) {
        List<Long> sharedIds = query.getUserId() == null ? List.of()
                : shareMapper.selectList(new LambdaQueryWrapper<CalendarShareEntity>()
                                .eq(CalendarShareEntity::getUserId, query.getUserId()))
                        .stream().map(CalendarShareEntity::getCalendarId).toList();
        LambdaQueryWrapper<CalendarEntryEntity> wrapper = new LambdaQueryWrapper<CalendarEntryEntity>()
                .and(query.getUserId() != null, item -> {
                    item.eq(CalendarEntryEntity::getOwnerId, query.getUserId())
                            .or().eq(CalendarEntryEntity::getVisibility, "PUBLIC");
                    if (!sharedIds.isEmpty()) {
                        item.or().in(CalendarEntryEntity::getId, sharedIds);
                    }
                })
                .eq(query.getUserId() == null, CalendarEntryEntity::getOwnerId, -1L)
                .and(StringUtils.hasText(query.getKeyword()), item -> item
                        .like(CalendarEntryEntity::getTitle, query.getKeyword())
                        .or().like(CalendarEntryEntity::getLocation, query.getKeyword())
                        .or().like(CalendarEntryEntity::getDescription, query.getKeyword()))
                .eq(StringUtils.hasText(query.getVisibility()), CalendarEntryEntity::getVisibility, query.getVisibility())
                .ge(query.getDateFrom() != null, CalendarEntryEntity::getEndTime,
                        query.getDateFrom() == null ? null : query.getDateFrom().atStartOfDay())
                .le(query.getDateTo() != null, CalendarEntryEntity::getStartTime,
                        query.getDateTo() == null ? null : query.getDateTo().atTime(23, 59, 59))
                .orderByAsc(CalendarEntryEntity::getStartTime)
                .orderByAsc(CalendarEntryEntity::getId);
        return wrapper;
    }

    private static CalendarEntry toEntry(CalendarEntryEntity entity) {
        CalendarEntry model = new CalendarEntry();
        model.setId(entity.getId());
        model.setOwnerId(entity.getOwnerId());
        model.setOwnerName(entity.getOwnerName());
        model.setTitle(entity.getTitle());
        model.setDescription(entity.getDescription());
        model.setLocation(entity.getLocation());
        model.setStartTime(entity.getStartTime());
        model.setEndTime(entity.getEndTime());
        model.setAllDay(entity.getAllDay());
        model.setVisibility(entity.getVisibility());
        model.setRemindMinutes(entity.getRemindMinutes());
        model.setSource(entity.getSource());
        model.setSourceId(entity.getSourceId());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    private static void fill(CalendarEntryEntity entity, CalendarEntry model) {
        entity.setOwnerId(model.getOwnerId());
        entity.setOwnerName(model.getOwnerName());
        entity.setTitle(model.getTitle());
        entity.setDescription(model.getDescription());
        entity.setLocation(model.getLocation());
        entity.setStartTime(model.getStartTime());
        entity.setEndTime(model.getEndTime());
        entity.setAllDay(model.getAllDay());
        entity.setVisibility(model.getVisibility());
        entity.setRemindMinutes(model.getRemindMinutes());
        entity.setSource(model.getSource());
        entity.setSourceId(model.getSourceId());
    }

    private static CalendarShare toShare(CalendarShareEntity entity) {
        CalendarShare model = new CalendarShare();
        model.setId(entity.getId());
        model.setCalendarId(entity.getCalendarId());
        model.setUserId(entity.getUserId());
        model.setUserName(entity.getUserName());
        model.setCreateTime(entity.getCreateTime());
        return model;
    }
}
