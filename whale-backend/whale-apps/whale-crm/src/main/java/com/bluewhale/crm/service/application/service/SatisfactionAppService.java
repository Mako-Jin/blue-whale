package com.bluewhale.crm.service.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.service.application.command.SurveyResponseCommand;
import com.bluewhale.crm.service.application.command.SurveySendCommand;
import com.bluewhale.crm.service.application.view.SatisfactionSurveyView;
import com.bluewhale.crm.service.application.view.SurveyStatsView;
import com.bluewhale.crm.service.domain.model.SurveyQuery;

/**
 * 满意度问卷应用服务。
 *
 * @author BlueWhale
 */
public interface SatisfactionAppService {

    /**
     * 分页查询问卷。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<SatisfactionSurveyView> page(SurveyQuery query, CrmActor actor);

    /**
     * 查看问卷详情（含答卷）。
     *
     * @param id 主键
     * @param actor 操作人
     * @return 视图
     */
    SatisfactionSurveyView detail(Long id, CrmActor actor);

    /**
     * 按工单生成或重发问卷。
     *
     * @param command 命令
     * @param actor 操作人
     * @return 视图（含一次性令牌）
     */
    SatisfactionSurveyView send(SurveySendCommand command, CrmActor actor);

    /**
     * 按一次性令牌提交答卷。
     *
     * @param command 命令
     * @param clientIp 提交来源 IP
     * @return 视图
     */
    SatisfactionSurveyView submit(SurveyResponseCommand command, String clientIp);

    /**
     * 作废问卷。
     *
     * @param id 主键
     * @param actor 操作人
     */
    void revoke(Long id, CrmActor actor);

    /**
     * 满意度统计。
     *
     * @param actor 操作人
     * @return 统计视图
     */
    SurveyStatsView stats(CrmActor actor);

    /**
     * 工单关闭后自动生成问卷（开关关闭或失败时静默跳过）。
     *
     * @param ticketId 工单 ID
     * @return 视图；未生成返回 null
     */
    SatisfactionSurveyView autoSend(Long ticketId);
}
