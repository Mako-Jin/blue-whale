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
     * <p>{@code keyword} 非空时内部转 {@link #search(ThreadQuery, ForumActor)}：
     * 列表页与检索页必须是同一份可见性判定，两套实现在改版块可见性时必然分叉。</p>
     *
     * @param query 查询条件
     * @param actor 当前操作者
     * @return 分页结果
     */
    PageResult<ThreadListItemVO> page(ThreadQuery query, ForumActor actor);

    /**
     * 关键词检索（ngram 全文索引，标题命中优先；关键词短于分词长度时回退 LIKE）。
     *
     * @param query 查询条件（{@code keyword} 必填）
     * @param actor 当前操作者
     * @return 分页结果
     */
    PageResult<ThreadListItemVO> search(ThreadQuery query, ForumActor actor);

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

    /**
     * 我的收藏分页（按收藏时间倒序，只含当前可见状态）。
     *
     * @param current 页码
     * @param size 条数
     * @param actor 当前操作者
     * @return 帖子分页
     */
    PageResult<ThreadListItemVO> collections(long current, long size, ForumActor actor);

    /**
     * 我的帖子分页（含审核中 / 已处置，作者要能看到自己的全部稿件）。
     *
     * @param current 页码
     * @param size 条数
     * @param actor 当前操作者
     * @return 帖子分页
     */
    PageResult<ThreadListItemVO> myThreads(long current, long size, ForumActor actor);

    /**
     * 结契动态分页：关注作者 ∪ 关注版块的已发布帖，排除本人与当前不可见版块。
     *
     * @param boardId 可选：只看某个版块（必须可见）
     * @param current 页码
     * @param size 条数
     * @param actor 当前操作者
     * @return 帖子分页
     */
    PageResult<ThreadListItemVO> myFeed(Long boardId, long current, long size, ForumActor actor);
}
