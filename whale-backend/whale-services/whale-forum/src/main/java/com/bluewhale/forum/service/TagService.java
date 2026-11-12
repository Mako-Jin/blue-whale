package com.bluewhale.forum.service;

import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.dto.TagStatVO;

import java.util.List;

/**
 * 标签聚合服务（F-06-07 标签页）。
 *
 * @author BlueWhale
 */
public interface TagService {

    /**
     * 标签聚合：仅统计当前用户可见版块内的已发布帖子，按帖子数降序。
     *
     * @param limit 取前 N 个标签（空/越界按配置兜底）
     * @param actor 当前操作者
     * @return 标签聚合列表
     */
    List<TagStatVO> list(Integer limit, ForumActor actor);
}
