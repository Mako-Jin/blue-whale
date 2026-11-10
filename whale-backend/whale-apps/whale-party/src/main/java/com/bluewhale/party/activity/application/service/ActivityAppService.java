package com.bluewhale.party.activity.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.activity.application.command.ActivitySaveCommand;
import com.bluewhale.party.activity.application.command.ActivitySigninCommand;
import com.bluewhale.party.activity.application.view.ActivityMetricView;
import com.bluewhale.party.activity.application.view.ActivityNoticeView;
import com.bluewhale.party.activity.application.view.ActivityView;
import com.bluewhale.party.activity.domain.model.PartyActivityQuery;
import com.bluewhale.party.common.PartyActor;

import java.time.LocalDate;
import java.util.List;

/**
 * 组织生活应用服务（M-P1）。
 *
 * @author BlueWhale
 */
public interface ActivityAppService {

    /**
     * 组织生活分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<ActivityView> page(PartyActivityQuery query, PartyActor actor);

    /**
     * 组织生活详情（含签到明细）。
     *
     * @param id 组织生活 ID
     * @param actor 操作人
     * @return 视图
     */
    ActivityView detail(Long id, PartyActor actor);

    /**
     * 新建组织生活计划。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 视图
     */
    ActivityView create(ActivitySaveCommand command, PartyActor actor);

    /**
     * 编辑组织生活（归档后只读）。
     *
     * @param id 组织生活 ID
     * @param command 保存命令
     * @param actor 操作人
     * @return 视图
     */
    ActivityView update(Long id, ActivitySaveCommand command, PartyActor actor);

    /**
     * 标记为已召开。
     *
     * @param id 组织生活 ID
     * @param heldDate 实际召开日期（空表示当天）
     * @param summary 会议纪要
     * @param actor 操作人
     * @return 视图
     */
    ActivityView complete(Long id, LocalDate heldDate, String summary, PartyActor actor);

    /**
     * 归档（终态）。
     *
     * @param id 组织生活 ID
     * @param actor 操作人
     * @return 视图
     */
    ActivityView archive(Long id, PartyActor actor);

    /**
     * 整场签到录入（同一人重复提交按更新处理）。
     *
     * @param id 组织生活 ID
     * @param command 签到命令
     * @param actor 操作人
     * @return 视图（含签到明细）
     */
    ActivityView signin(Long id, ActivitySigninCommand command, PartyActor actor);

    /**
     * 组织生活统计（M-P4，按统计日所在月份）。
     *
     * @param statDate 统计日（空表示当天）
     * @param branchId 支部 ID（空表示全租户）
     * @return 统计视图
     */
    ActivityMetricView metrics(LocalDate statDate, Long branchId);

    /**
     * 即将开始的党务活动（活动通知扫描用）。
     *
     * @param asOf 基准日
     * @return 活动列表
     */
    List<ActivityNoticeView> upcoming(LocalDate asOf);
}
