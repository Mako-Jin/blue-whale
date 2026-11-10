package com.bluewhale.oa.calendar.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.calendar.application.command.CalendarCommands;
import com.bluewhale.oa.calendar.application.view.CalendarViews;
import com.bluewhale.oa.calendar.domain.model.CalendarQuery;

import java.util.List;

/**
 * 日程应用服务。
 *
 * @author BlueWhale
 */
public interface CalendarAppService {

    /**
     * 分页查询当前用户可见日程。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CalendarViews.EntryView> page(CalendarQuery query);

    /**
     * 日程详情。
     *
     * @param id       日程ID
     * @param viewerId 查看人用户ID
     * @return 详情
     */
    CalendarViews.EntryDetailView detail(Long id, Long viewerId);

    /**
     * 新建日程。
     *
     * @param command 保存命令
     * @return 日程
     */
    CalendarViews.EntryView create(CalendarCommands.EntrySaveCommand command);

    /**
     * 编辑日程（仅所有者）。
     *
     * @param id         日程ID
     * @param command    保存命令
     * @param operatorId 操作人用户ID
     * @return 日程
     */
    CalendarViews.EntryView update(Long id, CalendarCommands.EntrySaveCommand command, Long operatorId);

    /**
     * 删除日程（仅所有者）。
     *
     * @param id         日程ID
     * @param operatorId 操作人用户ID
     */
    void delete(Long id, Long operatorId);

    /**
     * 替换共享名单（仅所有者）。
     *
     * @param id         日程ID
     * @param command    共享命令
     * @param operatorId 操作人用户ID
     * @return 最新共享名单
     */
    List<CalendarViews.ShareView> replaceShares(Long id, CalendarCommands.ShareSaveCommand command, Long operatorId);

    /**
     * 查询当前用户即将提醒的日程。
     *
     * @param ownerId 所有者用户ID
     * @param minutes 未来提醒窗口分钟数
     * @return 日程
     */
    List<CalendarViews.EntryView> reminders(Long ownerId, int minutes);
}
