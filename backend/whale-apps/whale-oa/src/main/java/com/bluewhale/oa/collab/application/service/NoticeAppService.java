package com.bluewhale.oa.collab.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.collab.application.command.CollabCommands;
import com.bluewhale.oa.collab.application.view.CollabViews;
import com.bluewhale.oa.collab.domain.model.CollabQuery;

import java.util.List;

/**
 * 公告应用服务。
 *
 * @author BlueWhale
 */
public interface NoticeAppService {

    /**
     * 公告分页（管理视角）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CollabViews.NoticeView> pageNotices(CollabQuery query);

    /**
     * 我的公告分页（按接收人）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CollabViews.NoticeView> pageMyNotices(CollabQuery query);

    /**
     * 公告详情。
     *
     * @param id 公告ID
     * @return 公告详情
     */
    CollabViews.NoticeDetailView noticeDetail(Long id);

    /**
     * 接收人候选。
     *
     * @param keyword 关键字
     * @param deptId 部门ID
     * @param limit 最大条数
     * @return 候选列表
     */
    List<CollabViews.CandidateView> recipientCandidates(String keyword, Long deptId, int limit);

    /**
     * 新建公告草稿。
     *
     * @param command 保存命令
     * @return 公告视图
     */
    CollabViews.NoticeView createNotice(CollabCommands.NoticeSaveCommand command);

    /**
     * 编辑公告草稿。
     *
     * @param id 公告ID
     * @param command 保存命令
     * @return 公告视图
     */
    CollabViews.NoticeView updateNotice(Long id, CollabCommands.NoticeSaveCommand command);

    /**
     * 发布公告。
     *
     * @param id 公告ID
     * @param operatorId 发布人用户ID
     * @param operator 发布人账号
     * @return 公告视图
     */
    CollabViews.NoticeView publishNotice(Long id, Long operatorId, String operator);

    /**
     * 撤回公告。
     *
     * @param id 公告ID
     * @return 公告视图
     */
    CollabViews.NoticeView withdrawNotice(Long id);

    /**
     * 标记公告已读。
     *
     * @param id 公告ID
     * @param userId 用户ID
     */
    void markRead(Long id, Long userId);

    /**
     * 已读统计。
     *
     * @param id 公告ID
     * @return 公告详情（含接收人阅读明细）
     */
    CollabViews.NoticeDetailView readStats(Long id);
}
