package com.bluewhale.forum.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.dto.ThreadCreateRequest;
import com.bluewhale.forum.dto.ThreadDetailVO;
import com.bluewhale.forum.dto.ThreadListItemVO;
import com.bluewhale.forum.dto.ThreadQuery;
import com.bluewhale.forum.dto.ThreadUpdateRequest;
import com.bluewhale.forum.entity.ForumThread;

/**
 * 帖子服务：发帖、列表、详情、编辑、删除。
 *
 * @author BlueWhale
 */
public interface ThreadService {

    /**
     * 发帖。审核版块直接落 {@code PENDING}，非审核版块落 {@code PUBLISHED}。
     *
     * @param request 发帖请求
     * @param actor 当前操作者
     * @return 新帖详情
     */
    ThreadDetailVO create(ThreadCreateRequest request, ForumActor actor);

    /**
     * 分页查询帖子列表。
     *
     * @param query 查询条件
     * @param actor 当前操作者
     * @return 分页结果
     */
    PageResult<ThreadListItemVO> page(ThreadQuery query, ForumActor actor);

    /**
     * 帖子详情（累加浏览数）。
     *
     * @param id 帖子ID
     * @param actor 当前操作者
     * @return 帖子详情
     */
    ThreadDetailVO detail(Long id, ForumActor actor);

    /**
     * 编辑帖子。审核版块内编辑自动退回待审并留原文快照。
     *
     * @param id 帖子ID
     * @param request 编辑请求
     * @param actor 当前操作者
     * @return 编辑后的详情
     */
    ThreadDetailVO update(Long id, ThreadUpdateRequest request, ForumActor actor);

    /**
     * 删除帖子（软删 + 改写帖子编号释放唯一键）。
     *
     * @param id 帖子ID
     * @param actor 当前操作者
     */
    void delete(Long id, ForumActor actor);

    /**
     * 取可读帖子（供楼层服务复用可见性判定，避免第二份实现）。
     *
     * <p>未发布内容仅作者与审核/处置角色可读，其他人一律按「不存在」返回。</p>
     *
     * @param id 帖子ID
     * @param actor 当前操作者
     * @return 帖子实体
     */
    ForumThread requireVisibleThread(Long id, ForumActor actor);
}
