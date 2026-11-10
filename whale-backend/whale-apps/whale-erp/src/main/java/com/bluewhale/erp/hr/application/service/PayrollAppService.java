package com.bluewhale.erp.hr.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.hr.application.command.PayrollCreateCommand;
import com.bluewhale.erp.hr.application.view.PayrollRunView;
import com.bluewhale.erp.hr.domain.model.HrQuery;

/**
 * 薪资批次应用服务。
 *
 * @author BlueWhale
 */
public interface PayrollAppService {

    /**
     * 分页查询薪资批次。
     *
     * @param query 查询条件
     * @return 批次分页
     */
    PageResult<PayrollRunView> page(HrQuery query);

    /**
     * 批次详情（含工资条）。
     *
     * @param id 主键
     * @return 批次
     */
    PayrollRunView detail(Long id);

    /**
     * 新建薪资批次（期间唯一）。
     *
     * @param command 创建命令
     * @return 批次
     */
    PayrollRunView create(PayrollCreateCommand command);

    /**
     * 计算薪资并生成工资条。
     *
     * @param id 主键
     * @return 批次
     */
    PayrollRunView calculate(Long id);

    /**
     * 审批薪资（生成计提凭证）。
     *
     * @param id 主键
     * @param operatorId 操作人
     * @return 批次
     */
    PayrollRunView approve(Long id, Long operatorId);

    /**
     * 发放薪资（生成发放凭证）。
     *
     * @param id 主键
     * @param operatorId 操作人
     * @return 批次
     */
    PayrollRunView pay(Long id, Long operatorId);

    /**
     * 结账。
     *
     * @param id 主键
     * @return 批次
     */
    PayrollRunView close(Long id);

    /**
     * 取消批次。
     *
     * @param id 主键
     * @return 批次
     */
    PayrollRunView cancel(Long id);

    /**
     * 我的工资条（按平台账号解析员工，仅返回本人）。
     *
     * @param userId 平台账号 ID
     * @param query 查询条件
     * @return 批次分页（仅本人工资条）
     */
    PageResult<PayrollRunView> myPayslips(Long userId, HrQuery query);
}
