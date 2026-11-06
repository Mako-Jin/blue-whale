package com.bluewhale.party.union.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.union.application.command.ReliefPayCommand;
import com.bluewhale.party.union.application.command.ReliefSaveCommand;
import com.bluewhale.party.union.application.view.UnionReliefSubmitView;
import com.bluewhale.party.union.application.view.UnionReliefView;
import com.bluewhale.party.union.domain.model.UnionReliefQuery;

/**
 * 慰问帮扶应用服务（M-P3）。
 *
 * @author BlueWhale
 */
public interface UnionReliefAppService {

    /**
     * 申请列表分页。
     *
     * @param query 查询条件
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<UnionReliefView> page(UnionReliefQuery query, Long current, Long size, PartyActor actor);

    /**
     * 申请详情。
     *
     * @param id 申请 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionReliefView detail(Long id, PartyActor actor);

    /**
     * 新建申请（草稿）。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 视图
     */
    UnionReliefView create(ReliefSaveCommand command, PartyActor actor);

    /**
     * 编辑申请（仅草稿可改）。
     *
     * @param id 申请 ID
     * @param command 保存命令
     * @param actor 操作人
     * @return 视图
     */
    UnionReliefView update(Long id, ReliefSaveCommand command, PartyActor actor);

    /**
     * 提交审批；审批不可用时保留草稿并返回降级提示。
     *
     * @param id 申请 ID
     * @param actor 操作人
     * @return 提交结果
     */
    UnionReliefSubmitView submit(Long id, PartyActor actor);

    /**
     * 同步审批结果。
     *
     * @param id 申请 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionReliefView sync(Long id, PartyActor actor);

    /**
     * 登记发放（仅审批通过可发放）。
     *
     * @param id 申请 ID
     * @param command 发放命令（可空）
     * @param actor 操作人
     * @return 视图
     */
    UnionReliefView pay(Long id, ReliefPayCommand command, PartyActor actor);
}
