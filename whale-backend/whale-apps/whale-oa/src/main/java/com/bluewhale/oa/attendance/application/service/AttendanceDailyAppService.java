package com.bluewhale.oa.attendance.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.view.AttendanceViews;
import com.bluewhale.oa.attendance.domain.model.AttendanceDailyQuery;

/**
 * 考勤日报应用服务。
 *
 * @author BlueWhale
 */
public interface AttendanceDailyAppService {

    /**
     * 分页查询考勤日报。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AttendanceViews.DailyView> page(AttendanceDailyQuery query);

    /**
     * 按日期区间重算日汇总。
     *
     * @param command 重算命令
     * @return 重算的日记录数
     */
    long recalculate(AttendanceCommands.RecalculateCommand command);
}
