package com.bluewhale.oa.culture.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.view.CultureViews;
import com.bluewhale.oa.culture.domain.model.CultureQuery;

/**
 * 投票问卷应用服务。
 *
 * @author BlueWhale
 */
public interface PollAppService {

    /**
     * 投票分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CultureViews.Poll> page(CultureQuery query);

    /**
     * 投票详情（含选项票数与我已投选项）。
     *
     * @param id 投票ID
     * @param userId 当前用户ID
     * @return 详情
     */
    CultureViews.PollDetail detail(Long id, Long userId);

    /**
     * 新建投票（草稿）。
     *
     * @param command 保存命令
     * @param creatorId 创建人用户ID
     * @param creatorName 创建人姓名
     * @return 投票
     */
    CultureViews.Poll create(CultureCommands.PollSave command, Long creatorId, String creatorName);

    /**
     * 编辑投票。
     *
     * @param id 投票ID
     * @param command 保存命令
     * @return 投票
     */
    CultureViews.Poll update(Long id, CultureCommands.PollSave command);

    /**
     * 删除投票。
     *
     * @param id 投票ID
     */
    void delete(Long id);

    /**
     * 发布投票。
     *
     * @param id 投票ID
     * @return 投票
     */
    CultureViews.Poll publish(Long id);

    /**
     * 关闭投票。
     *
     * @param id 投票ID
     * @return 投票
     */
    CultureViews.Poll close(Long id);

    /**
     * 投票。
     *
     * @param id 投票ID
     * @param command 投票命令
     * @param userId 投票人用户ID
     * @param userName 投票人姓名
     */
    void vote(Long id, CultureCommands.VoteCommand command, Long userId, String userName);
}
