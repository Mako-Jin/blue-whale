package com.bluewhale.oa.calendar.domain.repository;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.calendar.domain.model.CalendarEntry;
import com.bluewhale.oa.calendar.domain.model.CalendarQuery;
import com.bluewhale.oa.calendar.domain.model.CalendarShare;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程仓储。
 *
 * @author BlueWhale
 */
public interface CalendarRepository {

    /**
     * 查询日程。
     *
     * @param id 日程ID
     * @return 日程；不存在返回 null
     */
    CalendarEntry findEntry(Long id);

    /**
     * 分页查询当前用户可见日程。
     *
     * @param query 查询条件（userId 为查看人）
     * @return 分页结果
     */
    PageResult<CalendarEntry> pageEntries(CalendarQuery query);

    /**
     * 查询日程共享名单。
     *
     * @param calendarId 日程ID
     * @return 共享记录
     */
    List<CalendarShare> listShares(Long calendarId);

    /**
     * 判断指定用户是否在共享名单中。
     *
     * @param calendarId 日程ID
     * @param userId     用户ID
     * @return 是否已共享
     */
    boolean hasShare(Long calendarId, Long userId);

    /**
     * 新增日程并回填主键。
     *
     * @param entry 日程
     */
    void saveEntry(CalendarEntry entry);

    /**
     * 更新日程。
     *
     * @param entry 日程
     */
    void updateEntry(CalendarEntry entry);

    /**
     * 逻辑删除日程。
     *
     * @param id 日程ID
     */
    void deleteEntry(Long id);

    /**
     * 删除日程的全部共享记录。
     *
     * @param calendarId 日程ID
     */
    void deleteShares(Long calendarId);

    /**
     * 新增共享记录。
     *
     * @param share 共享记录
     */
    void saveShare(CalendarShare share);

    /**
     * 查询当前用户即将到期的提醒日程。
     *
     * @param ownerId 所有者用户ID
     * @param from    提醒时间窗口起
     * @param to      提醒时间窗口止
     * @return 日程
     */
    List<CalendarEntry> listReminders(Long ownerId, LocalDateTime from, LocalDateTime to);
}
