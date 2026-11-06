package com.bluewhale.party.union.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.union.application.command.ProposalFeedbackCommand;
import com.bluewhale.party.union.application.command.ProposalHandleCommand;
import com.bluewhale.party.union.application.command.ProposalRateCommand;
import com.bluewhale.party.union.application.command.ProposalSaveCommand;
import com.bluewhale.party.union.application.view.UnionProposalMetricView;
import com.bluewhale.party.union.application.view.UnionProposalView;
import com.bluewhale.party.union.domain.model.UnionProposalQuery;

import java.time.LocalDate;

/**
 * 职代会提案应用服务。
 *
 * @author BlueWhale
 */
public interface UnionProposalAppService {

    /**
     * 管理端提案分页。
     *
     * @param query 查询条件
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<UnionProposalView> page(UnionProposalQuery query, Long current, Long size, PartyActor actor);

    /**
     * 管理端提案详情。
     *
     * @param id 提案 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionProposalView detail(Long id, PartyActor actor);

    /**
     * 园地提交提案。
     *
     * @param command 提交命令
     * @param actor 操作人
     * @return 视图
     */
    UnionProposalView create(ProposalSaveCommand command, PartyActor actor);

    /**
     * 立案（可同时指定办理人）。
     *
     * @param id 提案 ID
     * @param command 办理人（可空）
     * @param actor 操作人
     * @return 视图
     */
    UnionProposalView file(Long id, ProposalHandleCommand command, PartyActor actor);

    /**
     * 办理。
     *
     * @param id 提案 ID
     * @param command 办理命令
     * @param actor 操作人
     * @return 视图
     */
    UnionProposalView handle(Long id, ProposalHandleCommand command, PartyActor actor);

    /**
     * 反馈。
     *
     * @param id 提案 ID
     * @param command 反馈命令
     * @param actor 操作人
     * @return 视图
     */
    UnionProposalView feedback(Long id, ProposalFeedbackCommand command, PartyActor actor);

    /**
     * 园地我的提案分页（强制只看本人）。
     *
     * @param query 查询条件
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<UnionProposalView> portalPage(UnionProposalQuery query, Long current, Long size, PartyActor actor);

    /**
     * 园地提案详情（非本人且非管理员按不存在处理）。
     *
     * @param id 提案 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionProposalView portalDetail(Long id, PartyActor actor);

    /**
     * 满意度评价（仅提交人本人）。
     *
     * @param id 提案 ID
     * @param command 评价命令
     * @param actor 操作人
     * @return 视图
     */
    UnionProposalView rate(Long id, ProposalRateCommand command, PartyActor actor);

    /**
     * 提案办理统计（M-P4，年度内提交口径）。
     *
     * @param statDate 统计日（空表示当天）
     * @return 统计视图
     */
    UnionProposalMetricView metrics(LocalDate statDate);
}
