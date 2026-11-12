package com.bluewhale.forum.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.dto.FollowStateVO;
import com.bluewhale.forum.dto.FollowVO;

/**
 * 结契（关注）服务。
 *
 * <p>关注对象只有作者（USER）与版块（BOARD）：关注帖子由藏帖承担，
 * 混进来会让新帖通知退化成一条条帖子订阅。</p>
 *
 * @author BlueWhale
 */
public interface FollowService {

    /** 关注作者 */
    String TARGET_USER = "USER";

    /** 关注版块 */
    String TARGET_BOARD = "BOARD";

    /**
     * 关注（幂等：已关注再点不报错）。
     *
     * @param targetType 对象类型：USER / BOARD
     * @param targetId 对象ID
     * @param actor 当前操作者
     * @return 关注态
     */
    FollowStateVO follow(String targetType, Long targetId, ForumActor actor);

    /**
     * 取关（幂等：未关注也返回成功）。
     *
     * @param targetType 对象类型：USER / BOARD
     * @param targetId 对象ID
     * @param actor 当前操作者
     * @return 关注态
     */
    FollowStateVO unfollow(String targetType, Long targetId, ForumActor actor);

    /**
     * 查询当前用户的关注态。
     *
     * @param targetType 对象类型：USER / BOARD
     * @param targetId 对象ID
     * @param actor 当前操作者
     * @return 关注态
     */
    FollowStateVO state(String targetType, Long targetId, ForumActor actor);

    /**
     * 我的关注（按关注时间倒序）。
     *
     * @param targetType 可选过滤：USER / BOARD
     * @param current 页码
     * @param size 每页条数
     * @param actor 当前操作者
     * @return 关注分页
     */
    PageResult<FollowVO> myFollows(String targetType, long current, long size, ForumActor actor);

    /**
     * 发布态新帖通知关注者（关注作者 + 关注版块）。
     *
     * <p>匿名帖只通知版块关注者：作者 identity 本身是隐藏的，
     * 推给「关注这个作者」的人等于把匿名帖跟作者关联起来。</p>
     *
     * @param threadId 帖子ID
     * @param boardId 版块ID
     * @param authorId 作者ID
     * @param title 帖子标题
     * @param anonymous 是否匿名：0 否 / 1 是
     * @return 实际入队通知数
     */
    int notifyNewThread(Long threadId, Long boardId, Long authorId, String title, Integer anonymous);
}
