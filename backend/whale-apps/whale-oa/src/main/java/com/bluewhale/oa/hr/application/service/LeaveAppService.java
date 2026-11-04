package com.bluewhale.oa.hr.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.hr.application.command.LeaveSaveCommand;
import com.bluewhale.oa.hr.application.command.LeaveStatusCommand;
import com.bluewhale.oa.hr.application.view.LeaveView;
import com.bluewhale.oa.hr.domain.model.LeaveQuery;

/**
 * 请假申请应用服务。
 *
 * @author BlueWhale
 */
public interface LeaveAppService {

    /**
     * 分页查询请假申请。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<LeaveView> page(LeaveQuery query);

    /**
     * 查询请假申请详情。
     *
     * @param id 主键
     * @return 申请视图
     */
    LeaveView detail(Long id);

    /**
     * 新增请假申请。
     *
     * @param command 保存命令
     * @return 新增后的申请
     */
    LeaveView create(LeaveSaveCommand command);

    /**
     * 更新请假申请。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 更新后的申请
     */
    LeaveView update(Long id, LeaveSaveCommand command);

    /**
     * 变更请假状态。
     *
     * @param id 主键
     * @param command 状态命令
     */
    void updateStatus(Long id, LeaveStatusCommand command);

    /**
     * 删除请假申请（逻辑删除）。
     *
     * @param id 主键
     */
    void delete(Long id);
}
