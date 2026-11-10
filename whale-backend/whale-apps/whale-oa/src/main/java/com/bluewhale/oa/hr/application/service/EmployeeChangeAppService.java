package com.bluewhale.oa.hr.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.hr.application.command.EmployeeChangeCommands;
import com.bluewhale.oa.hr.application.view.EmployeeChangeViews;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeQuery;

/**
 * 人员异动应用服务。
 *
 * @author BlueWhale
 */
public interface EmployeeChangeAppService {

    /**
     * 分页查询异动单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<EmployeeChangeViews.ListItem> page(EmployeeChangeQuery query);

    /**
     * 查询异动单详情（含离职清单任务）。
     *
     * @param id 异动单ID
     * @return 详情
     */
    EmployeeChangeViews.Detail detail(Long id);

    /**
     * 新建草稿。
     *
     * @param command 保存命令
     * @param applicantId 申请人ID
     * @param applicantName 申请人姓名
     * @return 详情
     */
    EmployeeChangeViews.Detail create(EmployeeChangeCommands.Save command, Long applicantId,
                                      String applicantName);

    /**
     * 更新草稿/已驳回/执行失败的异动单。
     *
     * @param id 异动单ID
     * @param command 保存命令
     * @return 详情
     */
    EmployeeChangeViews.Detail update(Long id, EmployeeChangeCommands.Save command);

    /**
     * 提交审批。
     *
     * @param id 异动单ID
     * @return 详情
     */
    EmployeeChangeViews.Detail submit(Long id);

    /**
     * 审批通过后执行（手工重试入口）。
     *
     * @param id 异动单ID
     * @param operatorId 操作人ID
     * @param operatorName 操作人姓名
     * @return 执行结果
     */
    EmployeeChangeViews.ExecuteResult execute(Long id, Long operatorId, String operatorName);

    /**
     * 完成离职清单任务。
     *
     * @param changeId 异动单ID
     * @param taskId 任务ID
     * @param remark 备注
     */
    void doneTask(Long changeId, Long taskId, String remark);

    /**
     * 同步审批结果（幂等）。
     *
     * @param changeId 异动单ID
     * @param result 审批结果
     */
    void syncApprovalResult(Long changeId, String result);
}
