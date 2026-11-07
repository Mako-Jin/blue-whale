package com.bluewhale.oa.attendance.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.view.AttendanceViews;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecordQuery;

/**
 * 考勤打卡应用服务。
 *
 * @author BlueWhale
 */
public interface AttendanceRecordAppService {

    /**
     * 员工打卡（同人同时间同类型幂等拦截）。
     *
     * @param userId 用户ID
     * @param command 打卡命令
     * @return 打卡记录
     */
    AttendanceViews.RecordView punch(Long userId, AttendanceCommands.PunchCommand command);

    /**
     * 分页查询打卡记录。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AttendanceViews.RecordView> page(AttendanceRecordQuery query);
}
