package com.bluewhale.lms.credit.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.credit.application.command.CreditAccrueCommand;
import com.bluewhale.lms.credit.application.view.CreditLedgerView;
import com.bluewhale.lms.credit.application.view.CreditMetricsView;
import com.bluewhale.lms.credit.application.view.CreditSummaryView;
import com.bluewhale.lms.credit.domain.model.CreditLedgerQuery;
import com.bluewhale.lms.credit.domain.model.CreditSource;

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

    /**
     * 业务动作触发的静默记账（学完课程、考过试）。
     *
     * <p>与 {@link #accrue} 的差别只有两点，但都是故意的：</p>
     * <ol>
     *   <li><b>没有规则时不报错也不落台账</b>：规则由管理员另行维护，学员不该因为
     *       「还没配规则」而被挡住学习或考试；</li>
     *   <li><b>任何异常都不外抛</b>：这是附赠动作，失败最多丢一份学分（可补记），
     *       但绝不能让已经学完的进度、已经交上去的卷子跟着回滚。</li>
     * </ol>
     *
     * <p>幂等仍由台账唯一键保证，重复触发（重学一遍、补考通过）不会重复记分。</p>
     *
     * @param source 来源类型
     * @param sourceId 来源业务 ID
     * @param userId 学员用户 ID
     * @param username 学员用户名（快照到台账）
     */
    void accrueQuietly(CreditSource source, Long sourceId, Long userId, String username);

    /**
     * 学分域指标（看板取数用）。
     *
     * @return 学分总量、获分人数与按人合计的分布
     */
    CreditMetricsView metrics();
}
