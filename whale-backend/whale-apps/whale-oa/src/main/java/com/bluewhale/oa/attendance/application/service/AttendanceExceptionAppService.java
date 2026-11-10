package com.bluewhale.oa.attendance.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.view.AttendanceViews;
import com.bluewhale.oa.attendance.domain.model.AttendanceExceptionQuery;

/**
 * 考勤异常应用服务。
 *
 * @author BlueWhale
 */
public interface AttendanceExceptionAppService {

    /**
     * 分页查询考勤异常。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AttendanceViews.ExceptionView> page(AttendanceExceptionQuery query);

    /**
     * 处理考勤异常（确认或豁免）。
     *
     * @param id 异常ID
     * @param command 处理命令
     * @param handler 处理人账号
     */
    void handle(Long id, AttendanceCommands.ExceptionHandleCommand command, String handler);
}
