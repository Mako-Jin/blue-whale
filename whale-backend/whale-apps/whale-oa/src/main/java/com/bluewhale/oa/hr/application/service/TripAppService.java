package com.bluewhale.oa.hr.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.hr.application.command.TripReportCommand;
import com.bluewhale.oa.hr.application.command.TripSaveCommand;
import com.bluewhale.oa.hr.application.command.TripStatusCommand;
import com.bluewhale.oa.hr.application.view.TripView;
import com.bluewhale.oa.hr.domain.model.TripQuery;

/**
 * 出差申请应用服务。
 *
 * <p>新增/更新时由服务端计算自然日天数、校验起止日期与预支不超过预算，并整体替换行程；
 * 审批通过且预支金额大于 0 时幂等生成预支记录；出差报告仅在审批通过后可提交一次。</p>
 *
 * @author BlueWhale
 */
public interface TripAppService {

    /**
     * 分页查询出差申请。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<TripView> page(TripQuery query);

    /**
     * 查询出差申请详情（含行程与预支）。
     *
     * @param id 主键
     * @return 申请视图
     */
    TripView detail(Long id);

    /**
     * 新增出差申请。
     *
     * @param command 保存命令
     * @return 新增后的申请
     */
    TripView create(TripSaveCommand command);

    /**
     * 更新出差申请。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 更新后的申请
     */
    TripView update(Long id, TripSaveCommand command);

    /**
     * 变更出差申请状态。
     *
     * @param id 主键
     * @param command 状态命令
     */
    void updateStatus(Long id, TripStatusCommand command);

    /**
     * 同步审批结果（APPROVED / REJECTED / CANCELLED），幂等。
     *
     * @param tripId 出差申请ID
     * @param result 审批结果
     */
    void syncApprovalResult(Long tripId, String result);

    /**
     * 提交出差报告。
     *
     * @param id 主键
     * @param command 报告命令
     */
    void submitReport(Long id, TripReportCommand command);

    /**
     * 删除出差申请（逻辑删除）。
     *
     * @param id 主键
     */
    void delete(Long id);
}
