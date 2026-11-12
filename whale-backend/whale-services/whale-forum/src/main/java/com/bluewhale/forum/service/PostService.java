package com.bluewhale.forum.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.dto.PostCreateRequest;
import com.bluewhale.forum.dto.PostVO;

/**
 * 楼层服务：回帖（楼层号分配）与楼层列表。
 *
 * @author BlueWhale
 */
public interface PostService {

    /**
     * 回帖。楼层号由服务端分配，冲突重试不超过 3 次。
     *
     * @param threadId 帖子ID
     * @param request 回帖请求
     * @param actor 当前操作者
     * @return 新楼层
     */
    PostVO create(Long threadId, PostCreateRequest request, ForumActor actor);

    /**
     * 分页查询楼层（默认按楼层号升序）。
     *
     * @param threadId 帖子ID
     * @param current 页码
     * @param size 每页条数
     * @param actor 当前操作者
     * @return 分页结果
     */
    PageResult<PostVO> page(Long threadId, Long current, Long size, ForumActor actor);

    /**
     * 分页查询楼层（指定排序方向）。
     *
     * <p><b>为什么排序做在服务端</b>：前端对单页数组反转，翻页时楼层会「跳」——
     * 第 1 页倒序的最后一条与第 2 页倒序的第一条之间隔着整页，不是相邻楼层。
     * 倒序必须写进 {@code ORDER BY ... DESC}，页码才有意义。</p>
     *
     * @param threadId 帖子ID
     * @param current 页码
     * @param size 每页条数
     * @param order 排序：{@code asc} 正序（默认）/ {@code desc} 倒序，其余值按正序处理
     * @param actor 当前操作者
     * @return 分页结果
     */
    PageResult<PostVO> page(Long threadId, Long current, Long size, String order, ForumActor actor);
}
