package com.bluewhale.party.union.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.union.application.command.WelfareIssueCommand;
import com.bluewhale.party.union.application.command.WelfareSaveCommand;
import com.bluewhale.party.union.application.view.UnionWelfareItemView;
import com.bluewhale.party.union.application.view.UnionWelfareView;
import com.bluewhale.party.union.domain.model.UnionWelfareQuery;

/**
 * 节日福利应用服务。
 *
 * @author BlueWhale
 */
public interface UnionWelfareAppService {

    /**
     * 批次分页。
     *
     * @param query 查询条件
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<UnionWelfareView> page(UnionWelfareQuery query, Long current, Long size, PartyActor actor);

    /**
     * 批次详情。
     *
     * @param id 批次 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionWelfareView detail(Long id, PartyActor actor);

    /**
     * 新建批次（草稿）。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 视图
     */
    UnionWelfareView create(WelfareSaveCommand command, PartyActor actor);

    /**
     * 编辑批次（仅草稿）。
     *
     * @param id 批次 ID
     * @param command 保存命令
     * @param actor 操作人
     * @return 视图
     */
    UnionWelfareView update(Long id, WelfareSaveCommand command, PartyActor actor);

    /**
     * 发放批次（生成名单并快照金额）。
     *
     * @param id 批次 ID
     * @param command 发放命令（名单可空）
     * @param actor 操作人
     * @return 视图（含本次新增条数）
     */
    UnionWelfareView issue(Long id, WelfareIssueCommand command, PartyActor actor);

    /**
     * 批次明细分页。
     *
     * @param id 批次 ID
     * @param signStatus 签收状态（可空）
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<UnionWelfareItemView> items(Long id, String signStatus, Long current, Long size, PartyActor actor);

    /**
     * 签收明细（本人或工会管理员）。
     *
     * @param itemId 明细 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionWelfareItemView sign(Long itemId, PartyActor actor);

    /**
     * 关闭批次（仅已发放）。
     *
     * @param id 批次 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionWelfareView close(Long id, PartyActor actor);

    /**
     * 我的福利分页。
     *
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<UnionWelfareItemView> my(Long current, Long size, PartyActor actor);
}
