package com.bluewhale.oa.attendance.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.view.AttendanceViews;
import com.bluewhale.oa.attendance.domain.model.AttendanceCorrectionQuery;

/**
 * 补卡申请应用服务。
 *
 * @author BlueWhale
 */
public interface AttendanceCorrectionAppService {

    /**
     * 提交补卡申请。
     *
     * @param userId 申请人用户ID
     * @param command 补卡命令
     * @return 补卡申请
     */
    AttendanceViews.CorrectionView create(Long userId, AttendanceCommands.CorrectionSaveCommand command);

    /**
     * 分页查询补卡申请。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AttendanceViews.CorrectionView> page(AttendanceCorrectionQuery query);

    /**
     * 审批结果回写（幂等）：通过后写入打卡记录并重算当日汇总。
     *
     * @param id 补卡申请ID
     * @param result 审批结果
     */
    void syncApprovalResult(Long id, String result);

    /**
     * 管理员手动变更状态。
     *
     * @param id 补卡申请ID
     * @param command 状态命令
     * @param handler 处理人账号
     */
    void updateStatus(Long id, AttendanceCommands.CorrectionStatusCommand command, String handler);
}
