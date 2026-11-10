package com.bluewhale.party.union.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.union.application.command.ActivitySaveCommand;
import com.bluewhale.party.union.application.view.UnionActivityMetricView;
import com.bluewhale.party.union.application.view.UnionActivityNoticeView;
import com.bluewhale.party.union.application.view.UnionActivityView;
import com.bluewhale.party.union.application.view.UnionSignupView;
import com.bluewhale.party.union.domain.model.UnionActivityQuery;

import java.time.LocalDate;
import java.util.List;

/**
 * 工会活动应用服务。
 *
 * @author BlueWhale
 */
public interface UnionActivityAppService {

    /**
     * 管理端活动分页。
     *
     * @param query 查询条件
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<UnionActivityView> page(UnionActivityQuery query, Long current, Long size, PartyActor actor);

    /**
     * 管理端活动详情。
     *
     * @param id 活动 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionActivityView detail(Long id, PartyActor actor);

    /**
     * 新建活动（草稿）。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 视图
     */
    UnionActivityView create(ActivitySaveCommand command, PartyActor actor);

    /**
     * 编辑活动（仅草稿）。
     *
     * @param id 活动 ID
     * @param command 保存命令
     * @param actor 操作人
     * @return 视图
     */
    UnionActivityView update(Long id, ActivitySaveCommand command, PartyActor actor);

    /**
     * 发布活动（仅草稿）。
     *
     * @param id 活动 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionActivityView publish(Long id, PartyActor actor);

    /**
     * 结束活动（仅已发布）。
     *
     * @param id 活动 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionActivityView close(Long id, PartyActor actor);

    /**
     * 取消活动（草稿或已发布）。
     *
     * @param id 活动 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionActivityView cancel(Long id, PartyActor actor);

    /**
     * 活动报名名单分页。
     *
     * @param id 活动 ID
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<UnionSignupView> signups(Long id, Long current, Long size, PartyActor actor);

    /**
     * 园地活动分页（仅已发布）。
     *
     * @param query 查询条件
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<UnionActivityView> portalPage(UnionActivityQuery query, Long current, Long size, PartyActor actor);

    /**
     * 园地活动详情（未发布对普通用户按不存在处理）。
     *
     * @param id 活动 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionActivityView portalDetail(Long id, PartyActor actor);

    /**
     * 报名（仅已发布且未开始）。
     *
     * @param id 活动 ID
     * @param actor 操作人
     * @return 报名视图
     */
    UnionSignupView signup(Long id, PartyActor actor);

    /**
     * 取消报名（仅本人）。
     *
     * @param id 活动 ID
     * @param actor 操作人
     * @return 报名视图
     */
    UnionSignupView cancelSignup(Long id, PartyActor actor);

    /**
     * 我的报名分页。
     *
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<UnionSignupView> my(Long current, Long size, PartyActor actor);

    /**
     * 工会活动统计（M-P4，年度内已结束活动）。
     *
     * @param statDate 统计日（空表示当天）
     * @return 统计视图
     */
    UnionActivityMetricView metrics(LocalDate statDate);

    /**
     * 即将开始的已发布活动（活动通知扫描用）。
     *
     * @param asOf 基准日
     * @return 活动列表
     */
    List<UnionActivityNoticeView> upcoming(LocalDate asOf);

    /**
     * 年度内去重参与人数（活动参与率分子）。
     *
     * @param from 起（含）
     * @param to 止（不含）
     * @return 去重参与人数
     */
    long distinctParticipants(LocalDate from, LocalDate to);
}
