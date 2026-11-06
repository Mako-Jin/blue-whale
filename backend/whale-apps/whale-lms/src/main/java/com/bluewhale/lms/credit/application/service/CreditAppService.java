package com.bluewhale.lms.credit.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.credit.application.command.CreditAccrueCommand;
import com.bluewhale.lms.credit.application.view.CreditLedgerView;
import com.bluewhale.lms.credit.application.view.CreditSummaryView;
import com.bluewhale.lms.credit.domain.model.CreditLedgerQuery;

/**
 * 学分台账应用服务。
 *
 * @author BlueWhale
 */
public interface CreditAppService {

    /**
     * 记账（同一业务事件幂等）。
     *
     * @param command 记账命令
     * @param actor 操作人
     * @return 台账 ID
     */
    Long accrue(CreditAccrueCommand command, LmsActor actor);

    /**
     * 分页查询台账（管理端）。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<CreditLedgerView> page(CreditLedgerQuery query, LmsActor actor);

    /**
     * 年度汇总。
     *
     * @param userId 学员用户 ID（为空取当前登录人）
     * @param periodYear 归属年度（为空表示全部年度）
     * @param actor 操作人
     * @return 汇总
     */
    CreditSummaryView summary(Long userId, Integer periodYear, LmsActor actor);

    /**
     * 我的学分台账。
     *
     * @param query 查询条件（学员强制为当前用户）
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<CreditLedgerView> mine(CreditLedgerQuery query, LmsActor actor);
}
