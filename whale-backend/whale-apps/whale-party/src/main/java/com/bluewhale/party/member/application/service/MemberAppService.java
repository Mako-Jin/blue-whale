package com.bluewhale.party.member.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.member.application.command.MemberSaveCommand;
import com.bluewhale.party.member.application.command.MemberTransferCommand;
import com.bluewhale.party.member.application.view.MemberMetricView;
import com.bluewhale.party.member.application.view.MemberNoticeTargetView;
import com.bluewhale.party.member.application.view.MemberView;
import com.bluewhale.party.member.application.view.ProbationCandidateView;
import com.bluewhale.party.member.domain.model.PartyMemberQuery;

import java.time.LocalDate;
import java.util.List;

/**
 * 党员台账应用服务（M-P1）。
 *
 * @author BlueWhale
 */
public interface MemberAppService {

    /**
     * 党员档案分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<MemberView> page(PartyMemberQuery query, PartyActor actor);

    /**
     * 党员档案详情。
     *
     * @param id 档案 ID
     * @param actor 操作人
     * @return 档案视图
     */
    MemberView detail(Long id, PartyActor actor);

    /**
     * 建档（一人一档，编号由流水生成）。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 档案视图
     */
    MemberView create(MemberSaveCommand command, PartyActor actor);

    /**
     * 编辑档案（已转出档案只读）。
     *
     * @param id 档案 ID
     * @param command 保存命令
     * @param actor 操作人
     * @return 档案视图
     */
    MemberView update(Long id, MemberSaveCommand command, PartyActor actor);

    /**
     * 办理组织关系转出（终态，档案保留只读）。
     *
     * @param id 档案 ID
     * @param command 转出命令
     * @param actor 操作人
     * @return 档案视图
     */
    MemberView transfer(Long id, MemberTransferCommand command, PartyActor actor);

    /**
     * 办理预备党员转正。
     *
     * @param id 档案 ID
     * @param fullMemberDate 转正日期（空表示当天）
     * @param actor 操作人
     * @return 档案视图
     */
    MemberView promote(Long id, LocalDate fullMemberDate, PartyActor actor);

    /**
     * 党员结构统计（M-P4，仅聚合数，不含个人明细）。
     *
     * @param statDate 统计日（空表示当天）
     * @param branchId 支部 ID（空表示全租户）
     * @return 结构统计
     */
    MemberMetricView metrics(LocalDate statDate, Long branchId);

    /**
     * 转正提醒候选（预备党员且距满一年不足提醒天数，含已逾期）。
     *
     * @param asOf 基准日
     * @return 候选列表
     */
    List<ProbationCandidateView> probationCandidates(LocalDate asOf);

    /**
     * 支部党员通知接收人（仅有 userId 的在册党员）。
     *
     * @param branchId 支部 ID
     * @return 接收人列表
     */
    List<MemberNoticeTargetView> noticeTargets(Long branchId);

    /**
     * 单个党员的通知接收人（党费催缴按账单指向的人取）。
     *
     * @param memberId 党员档案 ID
     * @return 接收人；档案不存在或未开通账号返回 null
     */
    MemberNoticeTargetView noticeTarget(Long memberId);

    /**
     * 按用户 ID 反查其党员档案所属支部（支部书记数据范围判定用）。
     *
     * @param userId 用户 ID
     * @return 支部 ID；无在册档案返回 null
     */
    Long branchIdOf(Long userId);
}
