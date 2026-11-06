package com.bluewhale.party.content.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.content.application.command.ContentRejectCommand;
import com.bluewhale.party.content.application.command.ContentSaveCommand;
import com.bluewhale.party.content.application.view.ContentAuditView;
import com.bluewhale.party.content.application.view.ContentReadView;
import com.bluewhale.party.content.application.view.ContentView;
import com.bluewhale.party.content.domain.model.ContentPortalQuery;
import com.bluewhale.party.content.domain.model.ContentQuery;

import java.util.List;

/**
 * 党建内容应用服务（M-P2，先审后发）。
 *
 * @author BlueWhale
 */
public interface ContentAppService {

    /**
     * 管理列表分页（含全部状态）。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<ContentView> page(ContentQuery query, PartyActor actor);

    /**
     * 管理详情（未审内容仅作者与审核人可见）。
     *
     * @param id 内容 ID
     * @param actor 操作人
     * @return 视图
     */
    ContentView detail(Long id, PartyActor actor);

    /**
     * 新建内容（草稿）。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 视图
     */
    ContentView create(ContentSaveCommand command, PartyActor actor);

    /**
     * 编辑内容；已发布内容编辑后自动退回待审。
     *
     * @param id 内容 ID
     * @param command 保存命令
     * @param actor 操作人
     * @return 视图
     */
    ContentView update(Long id, ContentSaveCommand command, PartyActor actor);

    /**
     * 提交审核（草稿 → 待审）。
     *
     * @param id 内容 ID
     * @param actor 操作人
     * @return 视图
     */
    ContentView submit(Long id, PartyActor actor);

    /**
     * 审核通过（待审 → 已发布）。
     *
     * @param id 内容 ID
     * @param actor 操作人
     * @return 视图
     */
    ContentView approve(Long id, PartyActor actor);

    /**
     * 审核退回（待审 → 草稿），意见必填。
     *
     * @param id 内容 ID
     * @param command 退回命令
     * @param actor 操作人
     * @return 视图
     */
    ContentView reject(Long id, ContentRejectCommand command, PartyActor actor);

    /**
     * 下线（已发布 → 已下线，终态，保留阅读数）。
     *
     * @param id 内容 ID
     * @param actor 操作人
     * @return 视图
     */
    ContentView offline(Long id, PartyActor actor);

    /**
     * 审核轨迹。
     *
     * @param id 内容 ID
     * @param actor 操作人
     * @return 轨迹列表
     */
    List<ContentAuditView> audits(Long id, PartyActor actor);

    /**
     * 党建园地阅读列表（仅已发布且当前用户可见）。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<ContentView> portalPage(ContentPortalQuery query, PartyActor actor);

    /**
     * 党建园地阅读详情（仅已发布且当前用户可见）。
     *
     * @param id 内容 ID
     * @param actor 操作人
     * @return 视图
     */
    ContentView portalDetail(Long id, PartyActor actor);

    /**
     * 记录阅读（同一人同一内容只计一次）。
     *
     * @param id 内容 ID
     * @param actor 操作人
     * @return 视图（含最新阅读人数）
     */
    ContentView read(Long id, PartyActor actor);

    /**
     * 阅读记录（内容作者与审核人可查）。
     *
     * @param id 内容 ID
     * @param actor 操作人
     * @return 阅读记录列表
     */
    List<ContentReadView> reads(Long id, PartyActor actor);
}
