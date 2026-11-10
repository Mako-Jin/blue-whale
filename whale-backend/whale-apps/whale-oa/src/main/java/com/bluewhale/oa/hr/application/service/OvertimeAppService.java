package com.bluewhale.oa.hr.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.hr.application.command.OvertimeSaveCommand;
import com.bluewhale.oa.hr.application.command.OvertimeStatusCommand;
import com.bluewhale.oa.hr.application.view.OvertimeView;
import com.bluewhale.oa.hr.domain.model.OvertimeQuery;

/**
 * 加班申请应用服务。
 *
 * <p>新增/更新时按起止时间计算加班时长并校验同日时段重叠与月度上限；
 * 审批通过且补偿方式为调休时，按 8 小时 = 1 天幂等调入调休假期额度。</p>
 *
 * @author BlueWhale
 */
public interface OvertimeAppService {

    /**
     * 分页查询加班申请。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<OvertimeView> page(OvertimeQuery query);

    /**
     * 查询加班申请详情。
     *
     * @param id 主键
     * @return 申请视图
     */
    OvertimeView detail(Long id);

    /**
     * 新增加班申请。
     *
     * @param command 保存命令
     * @return 新增后的申请
     */
    OvertimeView create(OvertimeSaveCommand command);

    /**
     * 更新加班申请。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 更新后的申请
     */
    OvertimeView update(Long id, OvertimeSaveCommand command);

    /**
     * 变更加班状态。
     *
     * @param id 主键
     * @param command 状态命令
     */
    void updateStatus(Long id, OvertimeStatusCommand command);

    /**
     * 同步审批结果（APPROVED / REJECTED / CANCELLED），幂等。
     *
     * @param overtimeId 加班申请ID
     * @param result 审批结果
     */
    void syncApprovalResult(Long overtimeId, String result);

    /**
     * 删除加班申请（逻辑删除）。
     *
     * @param id 主键
     */
    void delete(Long id);
}
