package com.bluewhale.erp.hr.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.hr.application.command.AttendanceSaveCommand;
import com.bluewhale.erp.hr.application.view.AttendanceView;
import com.bluewhale.erp.hr.domain.model.HrQuery;

/**
 * 考勤汇总应用服务。
 *
 * @author BlueWhale
 */
public interface AttendanceAppService {

    /**
     * 分页查询考勤汇总。
     *
     * @param query 查询条件
     * @return 考勤分页
     */
    PageResult<AttendanceView> page(HrQuery query);

    /**
     * 保存考勤（按员工 + 期间 upsert）。
     *
     * @param command 保存命令
     * @return 考勤
     */
    AttendanceView save(AttendanceSaveCommand command);
}
