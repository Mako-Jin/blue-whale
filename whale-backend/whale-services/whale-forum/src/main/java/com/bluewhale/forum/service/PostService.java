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
     * 分页查询楼层（按楼层号升序）。
     *
     * @param threadId 帖子ID
     * @param current 页码
     * @param size 每页条数
     * @param actor 当前操作者
     * @return 分页结果
     */
    PageResult<PostVO> page(Long threadId, Long current, Long size, ForumActor actor);
}
